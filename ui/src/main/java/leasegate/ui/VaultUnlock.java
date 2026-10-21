package leasegate.ui;

import java.nio.file.Path;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import leasegate.frame.*;
import leasegate.core.access.PassphraseGate;
import leasegate.vault.RecoveryKey;
import leasegate.vault.Vault;
import leasegate.vault.WrongKeyException;

/// Unlocking a gate that keeps secrets. One passphrase opens both the gate and its vault. On first
/// run the vault is created and its recovery key is shown once, to be written down; it is the only
/// way back in if the passphrase is forgotten, and with it a new passphrase can be set. Call on the
/// FX Application Thread, before anything else starts.
public final class VaultUnlock {

    public static Vault open(PassphraseGate gate, Path vaultFile, Brand brand) {
        var product = brand.product();
        if (!Vault.exists(vaultFile) && gate.hasPassphrase()) {
            var p = PassphraseUnlockDialog.unlockOrSetup(gate, brand);
            var created = Vault.create(vaultFile, p);
            showRecoveryKey(brand, created.recoveryKey());
            return created.vault();
        }
        if (!gate.hasPassphrase()) {
            var p = PassphraseUnlockDialog.unlockOrSetup(gate, brand,
                "After this you are shown a recovery key, once. Write it down: it is the only way back in if the passphrase is forgotten.");
            var created = Vault.create(vaultFile, p);
            showRecoveryKey(brand, created.recoveryKey());
            return created.vault();
        }
        while (true) {
            var p = askPassphrase(brand, gate.hint());
            if (p == null) { var v = recover(gate, vaultFile, brand); if (v != null) return v; continue; }
            if (!gate.unlock(p)) continue;
            try { return Vault.open(vaultFile, p); }
            catch (WrongKeyException e) { alert(brand, "The passphrase unlocks " + product + " but not its vault. Use the recovery key."); }
        }
    }

    /// The passphrase, or null when the human chose "I forgot it".
    static String askPassphrase(Brand brand, String hint) {
        var product = brand.product();
        var field = new SecretField();
        var result = new String[] { null };
        var forgot = new boolean[] { false };
        var frame = modal(brand, "locked").notClosable();
        var stage = frame.stage;
        var unlock = new Button("Unlock");
        unlock.setDefaultButton(true);
        unlock.setOnAction(e -> { result[0] = field.getText(); stage.close(); });
        var lost = new Hyperlink("I forgot the passphrase: use the recovery key");
        lost.setOnAction(e -> { forgot[0] = true; stage.close(); });
        var box = new VBox(10, new Label(product + " is locked. Enter the passphrase to unlock it and its vault."), field, new HBox(10, unlock, lost));
        if (!hint.isBlank()) box.getChildren().add(2, PassphraseUnlockDialog.hintLabel(hint));
        show(frame, box);
        return forgot[0] ? null : result[0] == null ? "" : result[0];
    }

    /// Recovery key, then a new passphrase for both the gate and the vault. Null if abandoned.
    static Vault recover(PassphraseGate gate, Path vaultFile, Brand brand) {
        var product = brand.product();
        var key = new TextField();
        key.setPromptText("XXXX-XXXX-...");
        var pass = new SecretField();
        var again = new SecretField();
        var error = new Label(" ");
        error.setStyle("-fx-text-fill: #b3261e;");
        var result = new Vault[1];
        var frame = modal(brand, "recover with the recovery key");
        var stage = frame.stage;
        var ok = new Button("Recover and set the new passphrase");
        ok.setDefaultButton(true);
        ok.setOnAction(e -> {
            if (!pass.getText().equals(again.getText())) { error.setText("the new passphrases don't match"); return; }
            if (!PassphraseGate.isValidPassphrase(pass.getText())) { error.setText("the passphrase cannot be blank"); return; }
            try {
                var v = Vault.openWithRecovery(vaultFile, key.getText());
                v.changePassphrase(pass.getText());
                gate.resetAfterRecovery(pass.getText());
                result[0] = v;
                stage.close();
            } catch (WrongKeyException | IllegalArgumentException x) { error.setText("that is not this vault's recovery key"); }
        });
        var cancel = new Button("Back");
        cancel.setOnAction(e -> stage.close());
        show(frame, new VBox(10, new Label("Type the recovery key you wrote down when " + product + " was set up, and choose a new passphrase."),
            key, new Label("new passphrase"), pass, again, error, new HBox(10, ok, cancel)));
        return result[0];
    }

    /// Shown once. The window cannot be closed until the last group has been typed back, which is
    /// the cheapest proof that it was written down rather than glanced at.
    static void showRecoveryKey(Brand brand, String recoveryKey) {
        var product = brand.product();
        var text = new TextField(recoveryKey);
        text.setEditable(false);
        text.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 14;");
        var last = recoveryKey.substring(recoveryKey.lastIndexOf('-') + 1);
        var confirm = new TextField();
        confirm.setPromptText("type the last group here");
        var done = new Button("I have written it down");
        done.setDefaultButton(true);
        var frame = modal(brand, "recovery key: write it down").notClosable();
        var stage = frame.stage;
        done.setOnAction(e -> { if (confirm.getText().trim().equalsIgnoreCase(last)) stage.close(); });
        show(frame, new VBox(10, new Label("This is " + product + "'s recovery key. It is shown this once and stored nowhere."
            + " Write it on paper or in a password manager. Without it, a forgotten passphrase means issuing every credential again."),
            text, new Label("To close this window, type its last four characters:"), confirm, done));
    }

    static GateFrame modal(Brand brand, String subtitle) { return new GateFrame(brand, subtitle).modal(); }

    static void show(GateFrame frame, VBox box) {
        box.setPadding(new Insets(16));
        box.setPrefWidth(520);
        box.getChildren().forEach(n -> { if (n instanceof Label l) l.setWrapText(true); });
        frame.scene(box);
        frame.stage.showAndWait();
    }

    static void alert(Brand brand, String message) {
        GateFrame.tell(brand, "vault", message);
    }

    private VaultUnlock() {}
}
