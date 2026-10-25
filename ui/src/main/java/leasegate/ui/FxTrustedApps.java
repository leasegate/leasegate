package leasegate.ui;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javafx.application.Platform;

import leasegate.frame.Brand;
import leasegate.core.access.*;
import leasegate.core.trusted.*;
import leasegate.core.win.PeerIdentity;

/// The trusted-app windows for a host gate: `FxTrustedApps.install(apps, rules, gate, brand)` and the
/// approval window and the unlock prompt appear when an app asks. The asking connection waits for
/// as long as the human takes; if the app exits meanwhile the window withdraws itself.
public final class FxTrustedApps<S extends Scope<S>> implements TrustedAppApproval<S> {

    final TrustedApps<S> apps;
    final LeaseRules<S> rules;
    final PassphraseGate gate;
    final Brand brand;

    FxTrustedApps(TrustedApps<S> apps, LeaseRules<S> rules, PassphraseGate gate, Brand brand) {
        this.apps = apps;
        this.rules = rules;
        this.gate = gate;
        this.brand = brand;
    }

    public static <S extends Scope<S>> TrustedApps<S> install(TrustedApps<S> apps, LeaseRules<S> rules, PassphraseGate gate, Brand brand) {
        return apps.human(new FxTrustedApps<>(apps, rules, gate, brand));
    }

    @Override public Optional<Decision> decide(Request<S> r) {
        var future = new CompletableFuture<Optional<Decision>>();
        var window = new CompletableFuture<TrustedAppWindow<S>>();
        Platform.runLater(() -> {
            var w = new TrustedAppWindow<>(r, gate, rules, brand, future::complete);
            window.complete(w);
            w.show();
        });
        return await(future, r.code(), () -> window.thenAccept(w -> Platform.runLater(w::withdraw)), Optional.empty());
    }

    @Override public boolean unlockFor(LongApproval<S> approval, Recognition.Recognized code) {
        var future = new CompletableFuture<Boolean>();
        var window = new CompletableFuture<TrustedUnlockWindow>();
        var app = apps.registry().show(approval.appId()).orElse(null);
        Platform.runLater(() -> {
            var w = new TrustedUnlockWindow(approval, code, app, gate, brand, future::complete);
            window.complete(w);
            w.show();
        });
        return await(future, code, () -> window.thenAccept(w -> Platform.runLater(() -> w.answer(false))), false);
    }

    static <T> T await(CompletableFuture<T> answer, Recognition.Recognized code, Runnable withdraw, T none) {
        var who = new PeerIdentity(code.pid(), code.exe(), Optional.of(code.start()));
        try {
            while (true) {
                try { return answer.get(1, TimeUnit.SECONDS); }
                catch (TimeoutException stillOpen) {
                    if (!FxApproval.alive(who)) { withdraw.run(); return none; }
                }
            }
        } catch (Exception e) { return none; }
    }
}
