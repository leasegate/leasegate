package leasegate.frame;

import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

/// A window that grants something must not grant it to a keystroke meant for another window. These
/// windows open on top and take the focus while someone may be typing elsewhere -- a word with an
/// "a" in it once approved a request -- so until the window has settled every key is swallowed and the
/// buttons are disabled, with a countdown saying so. Settling means two seconds with no key arriving:
/// each key swallowed starts the countdown again, so someone still typing stays locked out however
/// long the sentence. After that, acting takes a function key, never a letter, a digit, Space or Enter.
public final class Arming {

    static final int SECONDS = 2;

    public boolean armed;

    final List<Button> buttons;
    final Label line;
    final Timeline timer;
    int left;

    public Arming(List<Button> buttons, Label line) {
        this.buttons = buttons;
        this.line = line;
        this.timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        timer.setCycleCount(SECONDS);
        restart();
    }

    /// Swallows every key event of the scene while the window has not settled. Other filters of the same
    /// scene still run after a consume, so FKeys also asks `blocked(scene)` before acting.
    public Arming guard(Scene scene) {
        scene.getProperties().put(Arming.class, this);
        for (var type : List.of(KeyEvent.KEY_PRESSED, KeyEvent.KEY_TYPED, KeyEvent.KEY_RELEASED))
            scene.addEventFilter(type, e -> {
                if (armed) return;
                e.consume();
                if (type == KeyEvent.KEY_PRESSED) restart();
            });
        return this;
    }

    /// True while a guarded scene has not settled: no key may act in it.
    public static boolean blocked(Scene scene) {
        return scene.getProperties().get(Arming.class) instanceof Arming a && !a.armed;
    }

    void restart() {
        armed = false;
        left = SECONDS;
        buttons.forEach(b -> b.setDisable(true));
        say();
        timer.playFromStart();
    }

    void tick() {
        if (--left > 0) { say(); return; }
        armed = true;
        buttons.forEach(b -> b.setDisable(false));
        line.setText(" ");
    }

    void say() { line.setText("keys are ignored until " + left + "s have passed without one, so nothing typed elsewhere answers this"); }
}
