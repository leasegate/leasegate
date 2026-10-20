package leasegate.ui;

import java.util.function.Consumer;

import javafx.application.Platform;

import jarrunner.jr.apputils.JrApp;
import jarrunner.jr.apputils.UpdateResult.Status;
import jarrunner.jr.apputils.Updater;
import leasegate.frame.*;

/// "Check for updates": jr does the work (it knows where the gate's releases are, checks the
/// download and swaps the exe even while it runs), off the FX thread. A newer version is offered
/// with one question; it is used from the next start of the gate.
public final class UpdateCheck {

    static volatile boolean running;

    /// The version this exe was built as, or "development build" when not started by its exe.
    public static String version() { return JrApp.appVersion().orElse("development build"); }

    /// `report` gets one line for the user, on the FX thread, as the check goes.
    public static void run(Brand brand, Consumer<String> report) {
        if (running) return;
        running = true;
        report.accept("Checking for updates…");
        Thread.ofPlatform().daemon().name(brand.product() + "-update").start(() -> {
            try {
                var r = Updater.run(false, true);
                Platform.runLater(() -> {
                    if (r.status() != Status.NEWER) { report.accept(message(r.status(), r.message())); return; }
                    report.accept(brand.product() + " " + r.latest() + " is available (this is " + version() + ").");
                    if (GateFrame.ask(brand, "update", brand.product() + " " + r.latest() + " is available; this is " + version()
                        + ".\n\nUpdate now? The new version is used from the next start of " + brand.product()
                        + " (quit it from the tray and start it again; you will unlock it again).", "Update now", "Not now"))
                        update(brand, report);
                });
            } finally { running = false; }
        });
    }

    static void update(Brand brand, Consumer<String> report) {
        report.accept("Updating…");
        Thread.ofPlatform().daemon().name(brand.product() + "-update").start(() -> {
            var r = Updater.run(true, true);
            Platform.runLater(() -> report.accept(r.updated()
                ? "Updated to " + r.latest() + ". It runs from the next start of " + brand.product() + "."
                : message(r.status(), r.message())));
        });
    }

    static String message(Status status, String text) {
        var t = text == null || text.isBlank() ? "" : text.trim();
        return switch (status) {
            case CURRENT -> t.isEmpty() ? "Up to date (" + version() + ")." : t;
            case UNAVAILABLE -> "This copy was not started from its exe, so it cannot update itself.";
            default -> t.isEmpty() ? "The update check failed." : t;
        };
    }

    private UpdateCheck() {}
}
