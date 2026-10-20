package leasegate.core;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import leasegate.core.access.*;
import leasegate.core.access.Approval.Kind;
import leasegate.core.win.PeerIdentity;
import leasegate.transport.FrameIo;

/// Who may get through, and for how long, for one gate. It finds a grant that already covers a
/// request or asks the human, keeps the list of live links honest, and runs the sweep: expiry closes
/// open links rather than merely refusing new ones, so a row in the access window never reads as
/// ended while its client is still working; five minutes before an attested grant with a live link
/// runs out, the UI is told so the human can extend it (within the grant's own cap) or let it lapse.
public final class LeaseEngine<S extends Scope<S>> {

    /// `cut` wakes the link's blocked read so its own thread unwinds and closes it.
    public record LiveLink(Kind kind, String approvalId, String label, Runnable cut) {}

    public record Expiring(String grantId, String label, Instant expiresAt) {}

    static final long SWEEP_MS = 5_000;
    static final Duration WARN_BEFORE = Duration.ofMinutes(5);

    public final LeaseRules<S> rules;
    final Approval<S> approval;
    final GrantStore<S> grants = new GrantStore<>();
    final KeyedAppStore<S> keyed;
    final ConcurrentHashMap<Object, LiveLink> links = new ConcurrentHashMap<>();
    final Set<String> warned = ConcurrentHashMap.newKeySet();
    volatile Consumer<Expiring> onExpiring = e -> {};
    volatile TrustApproval<S> trustApproval = r -> Optional.empty();

    public LeaseEngine(LeaseRules<S> rules, Approval<S> approval, KeyedAppStore<S> keyed) {
        this.rules = rules;
        this.approval = approval;
        this.keyed = keyed;
    }

    public void onExpiring(Consumer<Expiring> l) { onExpiring = l; }

    public void trustApproval(TrustApproval<S> t) { trustApproval = t; }

    public LeaseEngine<S> startSweep() {
        Thread.ofPlatform().name(rules.product() + "-sweep").daemon().start(this::sweep);
        return this;
    }

    // -- deciding --

    /// An attested request: a grant that already covers it, or the human's answer. `raw` is the
    /// chain captured the moment the client connected.
    public Outcome<Grant<S>> attest(PeerIdentity peer, List<ProcessTree.RawLink> raw, S requested, String label) {
        var tree = ProcessTree.snapshot();
        var chain = tree.decorate(raw);
        var found = grants.findValid(peer.imagePath(), chain, requested);
        if (found.isPresent()) return Outcome.allowed(found.get());
        var d = approval.decide(new Approval.Request<>(Kind.ATTESTED, peer, chain, tree, requested, label, null, null));
        if (d.isEmpty()) return Outcome.refused("not approved", "the request was declined or its window closed");
        return Outcome.allowed(grants.create(peer.imagePath(), d.get().anchor(), d.get().durationMinutes(), d.get().capMinutes(),
            d.get().scope(), d.get().blanket()));
    }

    /// Only finds; never asks. For callers that must not block on a human (a second request that
    /// arrives while a window is already open for the same client).
    public Optional<Grant<S>> existing(PeerIdentity peer, List<ProcessTree.RawLink> raw, S requested) {
        return grants.findValid(peer.imagePath(), ProcessTree.snapshot().decorate(raw), requested);
    }

    /// A keyed request: the client proves its key on `io` by signing a fresh challenge, then an
    /// approval held by that key covers it or the human is asked.
    public Outcome<KeyedApp<S>> keyed(PeerIdentity peer, List<ProcessTree.RawLink> raw, String publicKey, S requested,
                                      String label, FrameIo io) {
        var nonce = KeyedAppStore.newChallenge();
        io.writeFrame(Replies.challenge(nonce));
        if (!KeyedAppStore.verify(publicKey, nonce, Handshake.signatureOf(io.readFrame())))
            return Outcome.refused("key proof failed", "the signature did not match the public key");
        var existing = keyed.byKey(publicKey);
        var now = Instant.now();
        if (existing.isPresent() && !existing.get().expired(now) && existing.get().scope().covers(requested))
            return Outcome.allowed(keyed.seen(existing.get(), peer));
        var reason = existing.isEmpty() ? "first request from this key" : existing.get().expired(now)
            ? "the earlier approval has expired" : "asks for more than was approved before";
        var tree = ProcessTree.snapshot();
        var d = approval.decide(new Approval.Request<>(Kind.KEYED, peer, tree.decorate(raw), tree, requested, label,
            KeyedAppStore.fingerprint(publicKey), reason));
        if (d.isEmpty()) return Outcome.refused("not approved", "the request was declined or its window closed");
        return Outcome.allowed(keyed.approve(label, publicKey, d.get().scope(), d.get().durationMinutes(), peer));
    }

    /// Trust an ancestor of the client -- `pid`, or the nearest agent session -- and everything it
    /// starts. Ends with a grant that covers any program under it, or a refusal.
    public Outcome<Grant<S>> trust(PeerIdentity peer, List<ProcessTree.RawLink> raw, S scope, String label, Long pid) {
        var tree = ProcessTree.snapshot();
        return switch (TrustTarget.pick(tree.decorate(raw), tree, pid)) {
            case TrustTarget.Refused r -> Outcome.refused(r.error(), r.hint());
            case TrustTarget.Found f -> {
                var d = trustApproval.decide(new TrustApproval.Request<>(peer, f.node(), scope, label));
                yield d.isEmpty() ? Outcome.refused("not trusted", "the request was declined or its window closed")
                    : Outcome.allowed(grants.create(null, f.node(), d.get().minutes(), TrustApproval.MAX_MINUTES, d.get().scope(), false));
            }
        };
    }

    // -- live links --

    public void register(Object link, Kind kind, String approvalId, String label, Runnable cut) {
        links.put(link, new LiveLink(kind, approvalId, label, cut));
    }

    public void unregister(Object link) { links.remove(link); }

    public List<Grant<S>> activeGrants() { return grants.active(); }

    /// A grant made without asking anyone: for a trial that needs one on the list, and for a gate
    /// whose own window has already asked (with `anchor` the process the human chose).
    public Grant<S> grant(String clientImagePath, AncestryNode anchor, int minutes, S scope) {
        return grants.create(clientImagePath, anchor, minutes, minutes, scope, false);
    }

    public List<KeyedApp<S>> keyedApps() { return keyed.all(); }

    public long liveConnections(String approvalId) { return links.values().stream().filter(l -> l.approvalId().equals(approvalId)).count(); }

    public void revokeGrant(String id) { grants.revoke(id); cut(id); }

    public void revokeKeyed(String id) { keyed.revoke(id); cut(id); }

    public void clearKeyedFlag(String id) { keyed.clearFlag(id); }

    /// Empty when the grant had already ended.
    public Optional<Grant<S>> extendGrant(String id) { var g = grants.extend(id); warned.remove(id); return g; }

    public void extendBlanketGrants(Instant newEnd) { grants.extendBlanketGrants(newEnd); }

    public void revokeAllNow() {
        grants.clear();
        links.values().forEach(l -> { if (l.kind() == Kind.ATTESTED) l.cut().run(); });
    }

    /// Whether this approval still stands -- for a gate whose links are one request each (an ssh
    /// signature, a token handed out) and so are never cut mid-way.
    public boolean stillValid(Kind kind, String approvalId) {
        return kind == Kind.KEYED ? keyed.isValid(approvalId) : grants.byId(approvalId).isPresent();
    }

    void cut(String approvalId) { links.values().forEach(l -> { if (l.approvalId().equals(approvalId)) l.cut().run(); }); }

    void sweep() {
        while (true) {
            try { Thread.sleep(SWEEP_MS); } catch (InterruptedException e) { return; }
            var now = Instant.now();
            links.values().forEach(l -> {
                if (l.kind() == Kind.KEYED) { if (!keyed.isValid(l.approvalId())) l.cut().run(); return; }
                var g = grants.byId(l.approvalId());
                if (g.isEmpty()) { l.cut().run(); return; }
                if (Duration.between(now, g.get().expiresAt()).compareTo(WARN_BEFORE) <= 0 && warned.add(g.get().id()))
                    onExpiring.accept(new Expiring(g.get().id(), l.label(), g.get().expiresAt()));
            });
        }
    }
}
