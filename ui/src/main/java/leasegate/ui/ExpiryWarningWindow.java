package leasegate.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import leasegate.frame.*;
import leasegate.core.LeaseEngine;
import leasegate.core.access.PassphraseGate;
import leasegate.core.access.Scope;

/// Five minutes before an attested grant with a live connection ends: extend it or let it lapse.
/// Extend adds the grant's own duration (at least half an hour) on top of what is left, never leaving
/// more than its cap, and the window shows the new end for a moment before closing so it is plain
/// that it worked. Closes itself at expiry. Keys are ignored for the first moments; F2 extends, Esc lets it lapse.
public final class ExpiryWarningWindow {

    public static <S extends Scope<S>> void show(LeaseEngine<S> engine, PassphraseGate gate, Brand brand, LeaseEngine.Expiring e) {
        var frame = new GateFrame(brand, "an approval is ending");
        var stage = frame.stage;
        var countdown = new Label();
        countdown.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");
        var done = new boolean[] { false };
        Runnable tick = () -> {
            if (done[0]) return;
            var s = Math.max(0, Duration.between(Instant.now(), e.expiresAt()).toSeconds());
            countdown.setText(e.label() + " loses access in " + "%d:%02d".formatted(s / 60, s % 60));
            if (s == 0) stage.close();
        };
        tick.run();
        var ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), x -> tick.run()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
        stage.setOnHidden(x -> ticker.stop());
        Runnable extend = () -> {
            if (done[0]) return;
            var g = engine.activeGrants().stream().filter(x -> x.id().equals(e.grantId())).findFirst();
            if (g.isEmpty()) GrantExtend.alreadyEnded(brand);
            var r = g.flatMap(x -> GrantExtend.run(engine, gate, brand, x));
            if (r.isEmpty()) { stage.close(); return; }
            done[0] = true;
            countdown.setText(e.label() + ": extended, " + GrantExtend.until(r.get()));
            var close = new PauseTransition(javafx.util.Duration.seconds(2));
            close.setOnFinished(x -> stage.close());
            close.play();
        };
        var extendButton = ApprovalParts.button("F2  Extend", "#2e6da8", extend);
        var lapseButton = ApprovalParts.button("Esc  Let it expire", "#777", stage::close);
        var buttons = new HBox(8, extendButton, lapseButton);
        var armingLine = ApprovalParts.line(" ", ApprovalParts.AMBER);
        var arming = new Arming(List.of(extendButton, lapseButton), armingLine);
        var root = new VBox(10, countdown, ApprovalParts.small("Extending keeps the same anchor and scope, and adds time up to the cap this approval first earned.", "#555"), buttons, armingLine);
        root.setPadding(new Insets(14));
        root.setPrefWidth(420);
        var scene = frame.scene(root);
        scene.setOnKeyPressed(k -> {
            if (!arming.armed) return;
            if (k.getCode() == KeyCode.F2) extend.run();
            else if (k.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setTitle(brand.product().toUpperCase() + ": approval ending");
        stage.show();
    }

    private ExpiryWarningWindow() {}
}
