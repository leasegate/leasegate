package leasegate.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import leasegate.frame.*;
import leasegate.core.access.PassphraseGate;

/// Tier 0: a gate needs a passphrase, set on first run, entered once to unlock the tray app --
/// nothing else starts (no pipe server, no tray icon) until this succeeds. Deliberately no escape
/// hatch: the window has no close button, so the only ways out of this screen are the right
/// passphrase or killing the process. Returns the passphrase typed, so a gate that keeps a vault
/// can open it with the same words; the caller must not keep it longer than that.
public final class PassphraseUnlockDialog {

    private PassphraseUnlockDialog() {}

    /// Blocks (via showAndWait) until the gate is unlocked. Must be called on the FX Application
    /// Thread, before anything else starts. `setupNote` is added to the first-run text.
    public static String unlockOrSetup(PassphraseGate gate, Brand brand, String setupNote) {
        var typed = new String[1];
        while (!gate.isUnlocked()) {
            if (gate.hasPassphrase()) promptUnlock(gate, brand, typed); else promptSetup(gate, brand, setupNote, typed);
        }
        return typed[0];
    }

    public static String unlockOrSetup(PassphraseGate gate, Brand brand) {
        return unlockOrSetup(gate, brand, "There is no reset: the gate stores only a hash, so write it down somewhere safe.");
    }

    static void promptUnlock(PassphraseGate gate, Brand brand, String[] typed) {
        var field = new SecretField();
        var error = errorLabel();
        var frame = new GateFrame(brand, "locked").modal().notClosable();
        var stage = frame.stage;
        var unlock = ApprovalParts.button("Enter  Unlock", ApprovalParts.GREEN, () -> {
            if (gate.unlock(field.getText())) { typed[0] = field.getText(); stage.close(); }
            else { error.setText("wrong passphrase"); field.clear(); }
        });
        unlock.setDefaultButton(true);

        var msg = new Label(brand.product() + " is locked. Enter the passphrase to unlock.");
        msg.setWrapText(true);
        var box = new VBox(10, msg, field, error, unlock);
        box.setPadding(new Insets(16));
        box.setPrefWidth(380);
        frame.scene(box);
        stage.showAndWait();
    }

    static void promptSetup(PassphraseGate gate, Brand brand, String setupNote, String[] typed) {
        var field = new SecretField();
        var confirm = new SecretField();
        var error = errorLabel();
        var frame = new GateFrame(brand, "first run: set the passphrase").modal().notClosable();
        var stage = frame.stage;
        var set = ApprovalParts.button("Enter  Set passphrase", ApprovalParts.GREEN, () -> {
            if (!field.getText().equals(confirm.getText())) { error.setText("passphrases don't match"); return; }
            if (!PassphraseGate.isValidPassphrase(field.getText())) { error.setText("the passphrase cannot be blank"); return; }
            if (!gate.setInitialPassphrase(field.getText())) { error.setText("could not set passphrase"); return; }
            typed[0] = field.getText();
            stage.close();
        });
        set.setDefaultButton(true);

        var msg = new Label("First run: set " + brand.product() + "'s passphrase. " + setupNote);
        msg.setWrapText(true);
        var box = new VBox(10, msg, field, confirm, error, set);
        box.setPadding(new Insets(16));
        box.setPrefWidth(420);
        frame.scene(box);
        stage.showAndWait();
    }

    static Label errorLabel() {
        var l = new Label(" ");
        l.setStyle("-fx-text-fill: #b3261e; -fx-font-size: 11;");
        return l;
    }
}
