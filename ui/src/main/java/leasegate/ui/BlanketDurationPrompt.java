package leasegate.ui;

import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import leasegate.frame.*;
import leasegate.core.access.PassphraseGate;

/// How long blanket mode should run, asked before the two passphrase prompts. F2 to F5 pick one of the
/// choices, Esc cancels. No digit, letter, Space or Enter picks anything, and nothing is picked until the
/// window has settled (see Arming): this window opens on top of whatever is being typed.
final class BlanketDurationPrompt {

    static final KeyCode[] KEYS = { KeyCode.F2, KeyCode.F3, KeyCode.F4, KeyCode.F5 };

    private BlanketDurationPrompt() {}

    /// The chosen minutes, or 0 on cancel.
    static int choose(Brand brand) {
        return choose(brand, "Blanket mode: any app, any request, with no approval window."
            + " It ends by itself after the time chosen here.");
    }

    static int choose(Brand brand, String intro) {
        var choices = PassphraseGate.BLANKET_MODE_CHOICES;
        var result = new int[] { 0 };
        var frame = new GateFrame(brand, "blanket mode").modal();
        var stage = frame.stage;

        var keys = new FKeys();
        for (var i = 0; i < choices.length; i++) {
            var minutes = choices[i];
            keys.bind(KEYS[i], DurationChips.label(minutes), brand.color(), () -> { result[0] = minutes; stage.close(); });
        }
        keys.bind(KeyCode.ESCAPE, "Cancel", "#777", stage::close);

        var msg = new Label(intro + " " + KEYS[0].getName() + " to " + KEYS[choices.length - 1].getName() + " pick a duration, Esc cancels.");
        msg.setWrapText(true);
        var line = Parts.line(" ", Parts.AMBER);
        var arming = new Arming(keys.bar.getChildren().stream().map(Button.class::cast).toList(), line);
        var box = new VBox(12, msg, keys.bar, line);
        box.setPadding(new Insets(16));
        box.setPrefWidth(520);
        var scene = frame.scene(box);
        arming.guard(scene);
        keys.install(scene);
        stage.showAndWait();
        return result[0];
    }
}
