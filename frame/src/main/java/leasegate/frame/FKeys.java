package leasegate.frame;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

/// The keys of a window that has text fields: letters belong to typing, so actions are function
/// keys, Enter commits and Esc cancels. Each binding is declared once and becomes both the key and
/// the button that wears it, so the row of buttons is the legend and every legend item is clickable.
/// A key that can be typed (a letter, a digit, Space) cannot be bound: it would answer a window the
/// human never meant to touch. Enter may be bound only where the window asks for a typed secret and
/// Enter submits it; the secret, not the key, is what guards the action.
public final class FKeys {

    final Map<KeyCode, Runnable> bound = new LinkedHashMap<>();
    public final HBox bar = new HBox(8);

    public FKeys bind(KeyCode key, String label, String color, Runnable action) {
        bind(key, action);
        bar.getChildren().add(Parts.button(name(key) + "  " + label, color, action));
        return this;
    }

    /// The key alone, for a window that lays out its own buttons: the button that does the same thing
    /// should wear the key's name in its label, and should be made mouse-only (see Parts.mouseOnly).
    public FKeys bind(KeyCode key, Runnable action) {
        if (!key.isFunctionKey() && key != KeyCode.ESCAPE && key != KeyCode.ENTER)
            throw new IllegalArgumentException(key + " can be typed, so it cannot be a shortcut: use a function key");
        bound.put(key, action);
        return this;
    }

    public void install(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (Arming.blocked(scene)) return;
            var a = bound.get(e.getCode());
            if (a != null) { a.run(); e.consume(); }
        });
    }

    /// Binds the keys for one part of a window, such as one tab: they work wherever the focus is in the
    /// window, but act only while `scope` is showing, so several tabs can each bind F2. Follows `scope`
    /// into whatever scene it is placed in.
    public void install(Node scope) {
        EventHandler<KeyEvent> handler = e -> {
            var a = bound.get(e.getCode());
            if (a == null || !showing(scope) || Arming.blocked(scope.getScene())) return;
            a.run();
            e.consume();
        };
        scope.sceneProperty().addListener((o, was, now) -> {
            if (was != null) was.removeEventFilter(KeyEvent.KEY_PRESSED, handler);
            if (now != null) now.addEventFilter(KeyEvent.KEY_PRESSED, handler);
        });
        if (scope.getScene() != null) scope.getScene().addEventFilter(KeyEvent.KEY_PRESSED, handler);
    }

    static boolean showing(Node scope) {
        for (var n = scope; n != null; n = n.getParent()) if (!n.isVisible()) return false;
        var window = scope.getScene() == null ? null : scope.getScene().getWindow();
        return window != null && window.isShowing();
    }

    static String name(KeyCode k) { return k == KeyCode.ESCAPE ? "Esc" : k.getName(); }
}
