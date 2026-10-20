package leasegate.ui;

import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import leasegate.core.access.PassphraseGate;

/// Tier 0: a gate needs a passphrase, set on first run, entered once to unlock the tray app --
/// nothing else starts (no pipe server, no tray icon) until this succeeds. Deliberately no escape
/// hatch on the window's close button: the only ways out of this screen are the right passphrase
/// or killing the process. Returns the passphrase typed, so a gate that keeps a vault can open it
/// with the same words; the caller must not keep it longer than that.
public final class PassphraseUnlockDialog {

    private PassphraseUnlockDialog() {}

    /// Blocks (via showAndWait) until the gate is unlocked. Must be called on the FX Application
    /// Thread, before anything else starts. `setupNote` is added to the first-run text.
    public static String unlockOrSetup(PassphraseGate gate, String product, String setupNote) {
        var typed = new String[1];
        while (!gate.isUnlocked()) {
            if (gate.hasPassphrase()) promptUnlock(gate, product, typed); else promptSetup(gate, product, setupNote, typed);
        }
        return typed[0];
    }

    public static String unlockOrSetup(PassphraseGate gate, String product) {
        return unlockOrSetup(gate, product, "There is no reset: the gate stores only a hash, so write it down somewhere safe.");
    }

    static void promptUnlock(PassphraseGate gate, String product, String[] typed) {
        var field = new PasswordField();
        var error = errorLabel();
        var unlock = new Button("Unlock");
        unlock.setDefaultButton(true);

        var msg = new Label(product + " is locked. Enter the passphrase to unlock.");
        msg.setWrapText(true);
        var box = new VBox(10, msg, field, error, unlock);
        box.setPadding(new Insets(16));
        box.setPrefWidth(360);

        var stage = new Stage(StageStyle.UTILITY);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(product + " — locked");
        unlock.setOnAction(e -> {
            if (gate.unlock(field.getText())) { typed[0] = field.getText(); stage.close(); }
            else { error.setText("wrong passphrase"); field.clear(); }
        });
        stage.setScene(new Scene(box));
        stage.setOnCloseRequest(Event::consume);
        stage.setAlwaysOnTop(true);
        stage.setOnShown(e -> stage.toFront());
        stage.showAndWait();
    }

    static void promptSetup(PassphraseGate gate, String product, String setupNote, String[] typed) {
        var field = new PasswordField();
        var confirm = new PasswordField();
        var error = errorLabel();
        var set = new Button("Set passphrase");
        set.setDefaultButton(true);

        var msg = new Label("First run: set " + product + "'s passphrase (letters and digits only). " + setupNote);
        msg.setWrapText(true);
        var box = new VBox(10, msg, field, confirm, error, set);
        box.setPadding(new Insets(16));
        box.setPrefWidth(400);

        var stage = new Stage(StageStyle.UTILITY);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(product + " — set up");
        set.setOnAction(e -> {
            if (!field.getText().equals(confirm.getText())) { error.setText("passphrases don't match"); return; }
            if (!PassphraseGate.isValidPassphrase(field.getText())) { error.setText("letters and digits only, not blank"); return; }
            if (!gate.setInitialPassphrase(field.getText())) { error.setText("could not set passphrase"); return; }
            typed[0] = field.getText();
            stage.close();
        });
        stage.setScene(new Scene(box));
        stage.setOnCloseRequest(Event::consume);
        stage.setAlwaysOnTop(true);
        stage.setOnShown(e -> stage.toFront());
        stage.showAndWait();
    }

    static Label errorLabel() {
        var l = new Label(" ");
        l.setStyle("-fx-text-fill: #b3261e; -fx-font-size: 11;");
        return l;
    }
}
