package leasegate.frame;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

/// The keys of a window that has text fields: letters belong to typing, so actions are function
/// keys, Enter commits and Esc cancels. Each binding is declared once and becomes both the key and
/// the button that wears it, so the row of buttons is the legend and every legend item is clickable.
public final class FKeys {

    final Map<KeyCode, Runnable> bound = new LinkedHashMap<>();
    public final HBox bar = new HBox(8);

    public FKeys bind(KeyCode key, String label, String color, Runnable action) {
        bound.put(key, action);
        bar.getChildren().add(Parts.button(name(key) + "  " + label, color, action));
        return this;
    }

    public void install(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            var a = bound.get(e.getCode());
            if (a != null) { a.run(); e.consume(); }
        });
    }

    static String name(KeyCode k) { return k == KeyCode.ESCAPE ? "Esc" : k.getName(); }
}
