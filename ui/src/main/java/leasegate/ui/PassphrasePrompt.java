package leasegate.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import leasegate.frame.*;
import leasegate.core.access.PassphraseGate;

/// Small modal passphrase prompts shared by Tier 1 (ApprovalWindow's per-connection re-confirm,
/// one entry) and Tier 2 (TrayShell's blanket-mode toggle, two entries) of the passphrase
/// gating. Must be called on the FX Application Thread -- each prompt blocks via showAndWait,
/// the same nested-event-loop pattern the rest of this UI already uses for confirmation dialogs.
public final class PassphrasePrompt {

    private PassphrasePrompt() {}

    /// One passphrase field, checked against the gate. Cancel or a wrong entry both return false;
    /// a wrong entry re-shows the same dialog with an error rather than closing it, so a mistyped
    /// passphrase does not need a second click to try again.
    public static boolean confirmOnce(PassphraseGate gate, Brand brand, String subtitle, String message) {
        var field = new SecretField();
        var error = errorLabel();
        var result = new boolean[] { false };
        var frame = new GateFrame(brand, subtitle).modal();
        var stage = frame.stage;
        var confirm = ApprovalParts.button("Enter  Confirm", ApprovalParts.GREEN, () -> {});
        confirm.setOnAction(e -> check(gate, field.getText(), confirm, error, () -> { result[0] = true; stage.close(); }, field::clear));
        confirm.setDefaultButton(true);
        var cancel = ApprovalParts.button("Esc  Cancel", "#555", stage::close);
        cancel.setCancelButton(true);

        var msg = new Label(message);
        msg.setWrapText(true);
        var box = new VBox(10, msg, field, error, new HBox(8, confirm, cancel));
        box.setPadding(new Insets(16));
        box.setPrefWidth(400);
        frame.scene(box);
        stage.showAndWait();
        return result[0];
    }

    /// The passphrase typed TWICE, in one window -- Tier 2's deliberate extra friction for the one mode
    /// that trusts everything. Both entries must match and the passphrase is checked once: two windows,
    /// each running the slow check on its own, made the human wait between them for no gain.
    public static boolean confirmTwice(PassphraseGate gate, Brand brand, String subtitle, String message) {
        var first = new SecretField();
        var second = new SecretField();
        var error = errorLabel();
        var result = new boolean[] { false };
        var frame = new GateFrame(brand, subtitle).modal();
        var stage = frame.stage;
        var confirm = ApprovalParts.button("Enter  Confirm", ApprovalParts.GREEN, () -> {});
        confirm.setOnAction(e -> {
            if (!first.getText().equals(second.getText())) { error.setText("the two entries differ"); second.clear(); return; }
            check(gate, first.getText(), confirm, error, () -> { result[0] = true; stage.close(); }, () -> { first.clear(); second.clear(); });
        });
        confirm.setDefaultButton(true);
        var cancel = ApprovalParts.button("Esc  Cancel", "#555", stage::close);
        cancel.setCancelButton(true);

        var msg = new Label(message);
        msg.setWrapText(true);
        var box = new VBox(10, msg, new Label("Passphrase"), first, new Label("Again, deliberately"), second, error, new HBox(8, confirm, cancel));
        box.setPadding(new Insets(16));
        box.setPrefWidth(400);
        frame.scene(box);
        stage.showAndWait();
        return result[0];
    }

    /// The check is slow by design (hundreds of thousands of key-derivation rounds), so it runs off the
    /// FX thread and the window says so instead of freezing.
    static void check(PassphraseGate gate, String passphrase, javafx.scene.control.Button confirm, Label error, Runnable ok, Runnable wrong) {
        confirm.setDisable(true);
        error.setStyle("-fx-text-fill: #555; -fx-font-size: 11;");
        error.setText("checking…");
        Thread.ofVirtual().start(() -> {
            var right = gate.verify(passphrase);
            javafx.application.Platform.runLater(() -> {
                confirm.setDisable(false);
                error.setStyle("-fx-text-fill: #b3261e; -fx-font-size: 11;");
                if (right) { error.setText(" "); ok.run(); }
                else { error.setText("wrong passphrase"); wrong.run(); }
            });
        });
    }

    static Label errorLabel() {
        var l = new Label(" ");
        l.setStyle("-fx-text-fill: #b3261e; -fx-font-size: 11;");
        return l;
    }
}
