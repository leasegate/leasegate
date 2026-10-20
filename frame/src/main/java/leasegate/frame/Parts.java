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

    public static Button button(String text, String color, Runnable action) {
        var b = new Button(text);
        b.setMnemonicParsing(false);
        b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold;");
        b.setOnAction(e -> action.run());
        return b;
    }

    private Parts() {}
}
