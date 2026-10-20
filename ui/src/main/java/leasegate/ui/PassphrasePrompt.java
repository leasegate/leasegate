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
        var confirm = ApprovalParts.button("Enter  Confirm", ApprovalParts.GREEN, () -> {
            if (gate.verify(field.getText())) { result[0] = true; stage.close(); }
            else { error.setText("wrong passphrase"); field.clear(); }
        });
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

    /// Two SEPARATE passphrase prompts, both must verify -- Tier 2's deliberate extra friction
    /// ("asks the passphrase TWICE") for the one mode that trusts any app, any profile, all
    /// websites. Short-circuits on the first cancel/wrong entry rather than showing the second.
    public static boolean confirmTwice(PassphraseGate gate, Brand brand, String subtitle, String message) {
        if (!confirmOnce(gate, brand, subtitle, message + "\n\n(1 of 2)")) return false;
        return confirmOnce(gate, brand, subtitle, message + "\n\n(2 of 2 -- confirming again, deliberately)");
    }

    static Label errorLabel() {
        var l = new Label(" ");
        l.setStyle("-fx-text-fill: #b3261e; -fx-font-size: 11;");
        return l;
    }
}
