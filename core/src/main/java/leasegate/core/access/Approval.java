package leasegate.core.access;

import java.util.List;
import java.util.Optional;

import leasegate.core.win.PeerIdentity;

/// The human decision. One window serves both kinds of request: an attested one, where the human
/// picks how far up the process tree the approval reaches, and a keyed one, where the approval is
/// held by the app's key and the tree is shown for information only. The decision's scope is
/// exactly what the human saw.
public interface Approval<S extends Scope<S>> {

    enum Kind { ATTESTED, KEYED }

    record Request<S extends Scope<S>>(Kind kind, PeerIdentity client, List<AncestryNode> chain, ProcessTree tree,
                                       S requested, String app, String keyFingerprint, String reason) {}

    /// `anchor` is null for a keyed decision.
    record Decision<S extends Scope<S>>(AncestryNode anchor, int durationMinutes, int capMinutes, S scope, boolean blanket) {
        public Decision(AncestryNode anchor, int durationMinutes, int capMinutes, S scope) { this(anchor, durationMinutes, capMinutes, scope, false); }
    }

    Optional<Decision<S>> decide(Request<S> request);
}
