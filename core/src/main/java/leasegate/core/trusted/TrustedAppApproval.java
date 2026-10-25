package leasegate.core.trusted;

import java.util.Optional;

import leasegate.core.access.Scope;
import leasegate.core.trusted.Recognition.Recognized;

/// The human side of trusted apps: the approval window, and the passphrase prompt shown on an app's
/// behalf. Both check the passphrase themselves before answering.
public interface TrustedAppApproval<S extends Scope<S>> {

    /// `previous` is the registry entry for this exe, if any (its old hashes are shown when the code
    /// changed); `check` is how far the code was verified just now; `hostLocked` is said in the
    /// window, because the approval keeps working through the lock.
    record Request<S extends Scope<S>>(Declaration declared, Recognized code, Optional<TrustedApp> previous,
                                       GitHubCheck.Result check, S scope, Integer requestedMinutes, int capMinutes,
                                       boolean hostLocked) {

        public boolean codeChanged() { return previous.isPresent() && !previous.get().runs(code); }
    }

    /// The passphrase is handed back so the host can take out the approval's material with it.
    record Decision(int minutes, String passphrase) {}

    Optional<Decision> decide(Request<S> request);

    /// The gate's passphrase prompt, shown for an app with an open approval that wants to open its
    /// own window. True only when the right passphrase was typed; the app never sees it.
    boolean unlockFor(LongApproval<S> approval, Recognized code);
}
