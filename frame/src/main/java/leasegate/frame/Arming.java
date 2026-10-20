package leasegate.frame;

import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.util.Duration;

/// A window that grants something must not grant it to a keystroke meant for another window. These
/// windows open on top and take the focus while someone may be typing elsewhere -- a word with an
/// "a" in it once approved a request -- so for the first moments every key is ignored and the
/// buttons are disabled, with a countdown saying so. After that, approving takes a function key,
/// never a letter.
public final class Arming {

    static final int SECONDS = 2;

    public boolean armed;

    public Arming(List<Button> buttons, Label line) {
        buttons.forEach(b -> b.setDisable(true));
        var left = new int[] { SECONDS };
        line.setText("keys are ignored for " + left[0] + "s, so nothing typed elsewhere answers this");
        var t = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            left[0]--;
            if (left[0] > 0) { line.setText("keys are ignored for " + left[0] + "s, so nothing typed elsewhere answers this"); return; }
            armed = true;
            buttons.forEach(b -> b.setDisable(false));
            line.setText(" ");
        }));
        t.setCycleCount(SECONDS);
        t.play();
    }
}
