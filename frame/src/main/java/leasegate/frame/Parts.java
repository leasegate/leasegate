package leasegate.frame;

import javafx.scene.control.Button;
import javafx.scene.control.Label;

/// The small building blocks every framed window uses: a coloured button, a bold coloured line,
/// a small note. Kept plain so windows built by different apps read alike.
public final class Parts {

    public static final String RED = "#b3261e", GREEN = "#2e7d32", GREY = "#666", AMBER = "#9a6700";

    public static Label line(String text, String color) {
        var l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        return l;
    }

    public static Label small(String text, String color) {
        var l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 10; -fx-text-fill: " + color + ";");
        return l;
    }

    /// A button the keyboard cannot reach: a focused button fires on Space, and a stray Space must not
    /// answer a window. It wears its key in its label and is clicked with the mouse.
    public static Button button(String text, String color, Runnable action) { return button(text, color, false, action); }

    /// The same button as a small chip, as in the proj picker: the colour as a pale tint with the colour itself as the text,
    /// a stronger tint under the pointer. On a dark window the tint is dimmer and the text a lightened colour. It is never
    /// narrower than its text, so a row of them wraps (see FKeys) instead of truncating.
    public static Button button(String text, String color, boolean dark, Runnable action) {
        var c = javafx.scene.paint.Color.web(color);
        var text2 = dark ? c.interpolate(javafx.scene.paint.Color.WHITE, 0.55) : c;
        var base = chipStyle(c, text2, dark ? 0.24 : 0.14);
        var over = chipStyle(c, text2, dark ? 0.42 : 0.28);
        var b = new Button(text);
        b.setMnemonicParsing(false);
        b.setFocusTraversable(false);
        b.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(over));
        b.setOnMouseExited(e -> b.setStyle(base));
        b.setOnAction(e -> action.run());
        return b;
    }

    static String chipStyle(javafx.scene.paint.Color tint, javafx.scene.paint.Color text, double alpha) {
        return "-fx-background-color: rgba(%d,%d,%d,%.2f); -fx-background-radius: 4; -fx-text-fill: %s; -fx-font-size: 11; -fx-padding: 3 9; -fx-cursor: hand;"
            .formatted(Math.round(tint.getRed() * 255), Math.round(tint.getGreen() * 255), Math.round(tint.getBlue() * 255), alpha, Glow.hex(text));
    }

    /// Makes every button under `root` one the keyboard cannot reach, as `button` does for its own:
    /// for a window whose buttons were built elsewhere and whose actions are on function keys.
    public static void mouseOnly(javafx.scene.Node root) {
        if (root instanceof javafx.scene.control.ButtonBase b) b.setFocusTraversable(false);
        if (root instanceof javafx.scene.Parent p)
            for (var child : p.getChildrenUnmodifiable()) mouseOnly(child);
    }

    private Parts() {}
}
