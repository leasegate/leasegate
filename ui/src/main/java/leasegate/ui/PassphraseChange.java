package leasegate.ui;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import leasegate.frame.*;
import leasegate.core.access.PassphraseGate;

/// Change the passphrase, and the hint shown when it is asked for. The current passphrase is proved
/// first. A gate that keeps a vault passes `reseal`: the vault's data key is sealed again under the
/// new passphrase before the gate takes it, so every stored secret stays as it is and the recovery
/// key keeps working. Call on the FX Application Thread.
public final class PassphraseChange {

    public static void show(PassphraseGate gate, Brand brand, Consumer<String> reseal) {
        var current = new SecretField();
        var fresh = new SecretField();
        var again = new SecretField();
        var hint = PassphraseUnlockDialog.hintField(gate.hint());
        var error = PassphraseUnlockDialog.errorLabel();
        var frame = new GateFrame(brand, "change the passphrase").modal();
        var stage = frame.stage;
        var change = ApprovalParts.button("Enter  Change passphrase", ApprovalParts.GREEN, () -> {
            if (!gate.verify(current.getText())) { error.setText("the current passphrase is wrong"); current.clear(); return; }
            var hintOnly = fresh.getText().isEmpty() && again.getText().isEmpty();
            if (!hintOnly) {
                if (!fresh.getText().equals(again.getText())) { error.setText("the new passphrases don't match"); return; }
                if (!PassphraseGate.isValidPassphrase(fresh.getText())) { error.setText("the passphrase cannot be blank"); return; }
                try { if (reseal != null) reseal.accept(fresh.getText()); }
                catch (RuntimeException e) { error.setText("the vault could not be sealed again: " + e.getMessage()); return; }
                if (!gate.change(current.getText(), fresh.getText())) { error.setText("could not change the passphrase"); return; }
            }
            gate.setHint(hint.getText());
            stage.close();
            GateFrame.tell(brand, "passphrase", hintOnly ? "The hint is saved." : "The passphrase is changed."
                + (reseal != null ? " Every stored secret is kept, and the recovery key still works." : ""));
        });
        change.setDefaultButton(true);

        var box = new VBox(10, label("Current passphrase"), current,
            label("New passphrase (leave both empty to change only the hint)"), fresh, again,
            label("Hint, shown when the passphrase is asked for (never the passphrase itself)"), hint, error, change);
        box.setPadding(new Insets(16));
        box.setPrefWidth(440);
        frame.scene(box);
        stage.showAndWait();
    }

    static Label label(String text) {
        var l = new Label(text);
        l.setWrapText(true);
        return l;
    }

    private PassphraseChange() {}
}
