package leasegate.ui;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javafx.application.Platform;

import leasegate.frame.*;
import leasegate.core.access.*;
import leasegate.core.win.PeerIdentity;

/// Bridges a connection thread's Approval.decide() to the approval window on the FX thread. The
/// connection waits for as long as the human takes: it is not usable until someone has looked. If the
/// requesting process exits while its window is open -- a timed-out command, a killed shell -- the
/// window withdraws itself, so the human is never asked to approve a request nobody is waiting on.
/// A gate whose requester gives up after a known time (a client with a read timeout) sets the same
/// limit here with `withTimeout`, so an answer never arrives for a request already abandoned.
public final class FxApproval<S extends Scope<S>> implements Approval<S> {

    final PassphraseGate passphraseGate;
    final LeaseRules<S> rules;
    final Brand brand;
    java.time.Duration timeout;

    public FxApproval(PassphraseGate passphraseGate, LeaseRules<S> rules, Brand brand) {
        this.passphraseGate = passphraseGate;
        this.rules = rules;
        this.brand = brand;
    }

    /// Withdraws the window, as a denial, when nobody has answered within `limit`. Without it the
    /// window waits for as long as the requesting process lives.
    public FxApproval<S> withTimeout(java.time.Duration limit) {
        this.timeout = limit;
        return this;
    }

    @Override public Optional<Decision<S>> decide(Request<S> request) {
        var future = new CompletableFuture<Optional<Decision<S>>>();
        var window = new CompletableFuture<ApprovalWindow<S>>();
        Platform.runLater(() -> {
            var w = new ApprovalWindow<>(request, passphraseGate, rules, brand, future::complete);
            window.complete(w);
            w.show();
        });
        var deadline = timeout == null ? Long.MAX_VALUE : System.nanoTime() + timeout.toNanos();
        try {
            while (true) {
                try { return future.get(1, TimeUnit.SECONDS); }
                catch (TimeoutException stillOpen) {
                    if (!alive(request.client()) || (timeout != null && System.nanoTime() - deadline >= 0)) {
                        window.thenAccept(w -> Platform.runLater(w::withdraw));
                        return Optional.empty();
                    }
                }
            }
        } catch (Exception e) { return Optional.empty(); }
    }

    /// Same pid AND same start time, so a reused pid does not keep a dead request's window open.
    static boolean alive(PeerIdentity p) {
        return ProcessHandle.of(p.pid()).filter(ProcessHandle::isAlive)
            .map(h -> p.startInstant().isEmpty() || h.info().startInstant().equals(p.startInstant())).orElse(false);
    }
}
