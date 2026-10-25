package leasegate.core.trusted;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import leasegate.core.access.*;
import leasegate.core.trusted.Recognition.*;

import static leasegate.transport.trusted.TrustedWire.*;

/// Trusted apps for one host gate: long approvals for resident client apps, bound to one run of the
/// app and to the exact code it runs. The host mounts `handle` on the loopback server it already
/// has, passing the pid the kernel names for the connection (never one the client states), and asks
/// `approvalFor(pid)` on each later request to learn whether that request is covered and with what.
///
/// Three operations, each answering one signed reply (see TrustedWire): `status` (is my approval
/// open, and until when, or why not), `request` (ask the human for one, through the approval window
/// with the passphrase), `unlock` (show the gate's passphrase prompt for me, so I may open my UI).
public final class TrustedApps<S extends Scope<S>> {

    final LeaseRules<S> rules;
    final PassphraseGate gate;
    final Consumer<String> audit;
    final TrustedAppRegistry registry;
    final LongApprovals<S> approvals;
    final GateSigner signer;
    final Recognizer recognizer = new Recognizer();
    final Set<Long> pending = ConcurrentHashMap.newKeySet();
    GitHubCheck github = new GitHubCheck();
    volatile TrustedAppApproval<S> human;
    volatile MaterialSource<S> material = (app, scope, passphrase) -> HeldMaterial.NONE;

    /// `dir` is the gate's own folder: `trusted-apps.json`, the reply key and `trusted-apps.pub` go there.
    public TrustedApps(Path dir, LeaseRules<S> rules, PassphraseGate gate, Consumer<String> audit) {
        this.rules = rules;
        this.gate = gate;
        this.audit = audit;
        this.registry = new TrustedAppRegistry(dir.resolve("trusted-apps.json"), audit);
        this.approvals = new LongApprovals<>(audit);
        this.signer = new GateSigner(dir);
    }

    public TrustedApps<S> human(TrustedAppApproval<S> h) { human = h; return this; }

    public TrustedApps<S> material(MaterialSource<S> m) { material = m; return this; }

    public TrustedApps<S> start() { approvals.startSweep(); return this; }

    public TrustedAppRegistry registry() { return registry; }

    /// What an app reads to check the gate's replies.
    public Path publicKeyFile() { return signer.publicKeyFile; }

    public List<LongApproval<S>> open() { return approvals.all(); }

    public void end(String approvalId, String why) { approvals.end(approvalId, why); }

    public int endAll(String why) { return approvals.endAll(why); }

    /// The open approval covering this connection's process, with its scope and material.
    public Optional<LongApproval<S>> approvalFor(long pid) {
        if (!(recognizer.recognize(pid) instanceof Recognized g)) return Optional.empty();
        return approvals.forProcess(pid, g.start(), Instant.now()).filter(a -> g.sameCode(a.exeSha256(), a.jarSha256()));
    }

    /// One request in, one signed reply out.
    public ObjectNode handle(long pid, JsonNode body) {
        var op = body.path("op").asText("");
        var nonce = body.path("nonce").asText("");
        if (!freshNonce(nonce)) return reply(op, REFUSED, nonce, pid, null, null).put("reason", "a fresh nonce of at least 16 random bytes is required");
        return switch (op) {
            case STATUS -> status(pid, nonce, body);
            case REQUEST -> request(pid, nonce, body);
            case UNLOCK -> unlock(pid, nonce);
            default -> reply(op, REFUSED, nonce, pid, null, null).put("reason", "unknown op '" + op + "'");
        };
    }

    ObjectNode status(long pid, String nonce, JsonNode body) {
        var r = body.hasNonNull("app") ? recognizer.recognize(pid, Declaration.of(body.get("app"))) : recognizer.recognize(pid);
        if (!(r instanceof Recognized g)) return refused(STATUS, nonce, (Refused) r);
        var now = Instant.now();
        approvals.sweep(now);
        var live = approvals.forProcess(pid, g.start(), now);
        if (live.isPresent()) return reply(STATUS, OPEN, nonce, pid, g, live.get());
        return notOpen(STATUS, nonce, g);
    }

    ObjectNode notOpen(String op, String nonce, Recognized g) {
        var entry = registry.byExe(g.exe());
        if (entry.isEmpty()) return reply(op, NOT_TRUSTED, nonce, g.pid(), g, null).put("reason", "this app is not trusted yet; send a request");
        if (!entry.get().runs(g)) return reply(op, CODE_CHANGED, nonce, g.pid(), g, null)
            .put("reason", "its exe or jar is not the code that was trusted; a new request shows the human both");
        var how = approvals.lastEnd(g.pid(), g.start());
        if (how.isEmpty()) return reply(op, NOT_OPEN, nonce, g.pid(), g, null).put("reason", "trusted, but no approval is open for this run; send a request");
        return reply(op, how.get() == LongApprovals.Ended.EXPIRED ? EXPIRED : ENDED, nonce, g.pid(), g, null)
            .put("reason", how.get() == LongApprovals.Ended.EXPIRED ? "the approval ran its time" : "the approval was ended");
    }

    ObjectNode request(long pid, String nonce, JsonNode body) {
        var declared = Declaration.of(body.path("app"));
        var r = recognizer.recognize(pid, declared);
        if (!(r instanceof Recognized g)) return refused(REQUEST, nonce, (Refused) r);
        var scope = rules.readScope(body);
        var live = approvals.forProcess(pid, g.start(), Instant.now()).filter(a -> a.scope().covers(scope));
        if (live.isPresent()) return reply(REQUEST, OPEN, nonce, pid, g, live.get());
        if (human == null) return reply(REQUEST, REFUSED, nonce, pid, g, null).put("reason", "this gate cannot ask anyone");
        if (!pending.add(pid)) return reply(REQUEST, PENDING, nonce, pid, g, null).put("reason", "a window for this app is already open");
        try {
            recognizer.forgetDead();
            return ask(nonce, g, declared, scope, body.hasNonNull("minutes") ? body.get("minutes").asInt() : null);
        } finally { pending.remove(pid); }
    }

    ObjectNode ask(String nonce, Recognized g, Declaration declared, S scope, Integer minutes) {
        var previous = registry.byExe(g.exe());
        var check = github.check(declared, g);
        var cap = Math.min(TrustedApp.MAX_MINUTES, previous.map(TrustedApp::maxMinutes).orElse(TrustedApp.MAX_MINUTES));
        var d = human.decide(new TrustedAppApproval.Request<>(declared, g, previous, check, scope, minutes, cap, !gate.isUnlocked()));
        if (d.isEmpty()) return reply(REQUEST, DECLINED, nonce, g.pid(), g, null).put("reason", "the request was declined or its window closed");
        var now = Instant.now();
        var verified = check instanceof GitHubCheck.Verified v ? v : null;
        var entry = registry.put(new TrustedApp(previous.map(TrustedApp::id).orElse(UUID.randomUUID().toString()), declared.name(),
            g.exe(), g.exeSha256(), g.jar(), g.jarSha256(), declared.project(), declared.assets(),
            verified == null ? TrustedApp.Level.DEV : TrustedApp.Level.VERIFIED, verified == null ? null : verified.org() + "/" + verified.repo() + " " + verified.tag(),
            verified == null ? null : verified.at(), cap, now, now));
        HeldMaterial held;
        try { held = material.take(entry, rules.stored(scope), d.get().passphrase()); }
        catch (RuntimeException e) { return reply(REQUEST, REFUSED, nonce, g.pid(), g, null).put("reason", "the host could not prepare this approval: " + e.getMessage()); }
        var approval = approvals.add(new LongApproval<>(UUID.randomUUID().toString(), entry.id(), entry.name(), g.pid(), g.start(),
            g.exeSha256(), g.jarSha256(), rules.stored(scope), now, now.plusSeconds(Math.clamp(d.get().minutes(), 1, cap) * 60L), held));
        return reply(REQUEST, OPEN, nonce, g.pid(), g, approval);
    }

    ObjectNode unlock(long pid, String nonce) {
        if (!(recognizer.recognize(pid) instanceof Recognized g)) return refused(UNLOCK, nonce, (Refused) recognizer.recognize(pid));
        var live = approvals.forProcess(pid, g.start(), Instant.now());
        if (live.isEmpty()) return notOpen(UNLOCK, nonce, g);
        if (human == null) return reply(UNLOCK, REFUSED, nonce, pid, g, live.get()).put("reason", "this gate cannot ask anyone");
        if (!pending.add(pid)) return reply(UNLOCK, PENDING, nonce, pid, g, live.get()).put("reason", "a window for this app is already open");
        try {
            var ok = human.unlockFor(live.get(), g);
            audit.accept("passphrase prompt for " + live.get().appName() + " (pid " + pid + "): " + (ok ? "unlocked" : "not unlocked"));
            return reply(UNLOCK, ok ? UNLOCKED : DECLINED, nonce, pid, g, live.get());
        } finally { pending.remove(pid); }
    }

    ObjectNode refused(String op, String nonce, Refused r) {
        var state = leasegate.transport.LeasePlatform.WINDOWS ? REFUSED : UNSUPPORTED;
        return reply(op, state, nonce, r.pid(), null, null).put("reason", r.reason()).put("hint", r.hint());
    }

    ObjectNode reply(String op, String state, String nonce, long pid, Recognized g, LongApproval<S> a) {
        var n = GateSigner.M.createObjectNode().put("ok", OPEN.equals(state) || UNLOCKED.equals(state)).put("op", op).put("state", state)
            .put("nonce", nonce).put("pid", Long.toString(pid)).put("hostLocked", Boolean.toString(!gate.isUnlocked()))
            .put("issuedAt", Instant.now().toString());
        if (g != null) n.put("start", Long.toString(g.start().toEpochMilli())).put("exeSha256", g.exeSha256()).put("jarSha256", g.jarSha256());
        else ProcessHandle.of(pid).flatMap(p -> p.info().startInstant()).ifPresent(s -> n.put("start", Long.toString(s.toEpochMilli())));
        if (a != null) n.put("expiresAt", a.expiresAt().toString()).put("app", a.appName());
        if (a != null && OPEN.equals(state) && !gate.isUnlocked()) n.put("note", "the host is locked; this approval keeps working until " + a.expiresAt());
        return signer.sign(n);
    }

    static boolean freshNonce(String nonce) {
        try { return Base64.getDecoder().decode(nonce).length >= 16; } catch (IllegalArgumentException e) { return false; }
    }
}
