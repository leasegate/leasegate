package leasegate.ui;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javafx.application.Platform;

import leasegate.frame.*;
import leasegate.core.access.*;

/// Bridges a trust request to its window on the FX thread, the same way FxApproval does for ordinary
/// requests: waits as long as the human takes, and withdraws the window if the asking process exits.
public final class FxTrust<S extends Scope<S>> implements TrustApproval<S> {

    final PassphraseGate gate;
    final LeaseRules<S> rules;
    final Brand brand;

    public FxTrust(PassphraseGate gate, LeaseRules<S> rules, Brand brand) {
        this.gate = gate;
        this.rules = rules;
        this.brand = brand;
    }

    @Override public Optional<Decision<S>> decide(Request<S> request) {
        var future = new CompletableFuture<Optional<Decision<S>>>();
        var window = new CompletableFuture<TrustWindow<S>>();
        Platform.runLater(() -> {
            var w = new TrustWindow<>(request, gate, rules, brand, future::complete);
            window.complete(w);
            w.show();
        });
        try {
            while (true) {
                try { return future.get(1, TimeUnit.SECONDS); }
                catch (TimeoutException stillOpen) {
                    if (!FxApproval.alive(request.client())) {
                        window.thenAccept(w -> Platform.runLater(w::withdraw));
                        return Optional.empty();
                    }
                }
            }
        } catch (Exception e) { return Optional.empty(); }
    }
}
