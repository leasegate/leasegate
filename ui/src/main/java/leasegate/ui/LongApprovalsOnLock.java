package leasegate.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import leasegate.frame.*;
import leasegate.core.trusted.TrustedApps;

/// Asked when the human locks the host on purpose (tray menu, CLI, hotkey), and only then: long
/// approvals keep working through a lock, so this lists them and lets one key end them all (F2) or
/// keep them running (F3, or Esc). An idle lock or Win+L never asks and never ends them. Call on the
/// FX thread, before or after locking; it returns how many were ended.
public final class LongApprovalsOnLock {

    public static int ask(Brand brand, TrustedApps<?> apps) {
        var open = apps.open();
        if (open.isEmpty()) return 0;
        var end = new boolean[] {false};
        var frame = new GateFrame(brand, "locking: long approvals are still open").modal();
        var stage = frame.stage;
        var keys = new FKeys()
            .bind(KeyCode.F2, "End them all", ApprovalParts.RED, () -> { end[0] = true; stage.close(); })
            .bind(KeyCode.F3, "Keep them running", "#555", stage::close)
            .bind(KeyCode.ESCAPE, stage::close);
        var intro = new Label("You are locking " + brand.product() + ". These approvals keep working through a lock until their own time ends:");
        intro.setWrapText(true);
        var root = new VBox(8, intro);
        for (var a : open)
            root.getChildren().add(ApprovalParts.line("•  " + a.appName() + " (pid " + a.pid() + ") until " + TrustedParts.when(a.expiresAt()), ApprovalParts.GREY));
        root.getChildren().add(keys.bar);
        root.setPadding(new Insets(14));
        root.setPrefWidth(480);
        keys.install(frame.scene(root));
        stage.showAndWait();
        return end[0] ? apps.endAll("ended by the human when locking " + brand.product()) : 0;
    }

    private LongApprovalsOnLock() {}
}
