package leasegate.core.access;

import java.util.Optional;

import leasegate.core.win.PeerIdentity;

/// A process asks that one of its own ancestors -- by default the nearest agent session, such as
/// claude.exe -- be trusted, together with every program it starts, for hours rather than minutes.
/// No heuristic caps apply here; the human decides with the passphrase, and the window's colour
/// says how much is being handed over.
public interface TrustApproval<S extends Scope<S>> {

    int MAX_MINUTES = 48 * 60;

    record Request<S extends Scope<S>>(PeerIdentity client, AncestryNode target, S requested, String app) {}

    record Decision<S extends Scope<S>>(int minutes, S scope) {}

    Optional<Decision<S>> decide(Request<S> request);
}
