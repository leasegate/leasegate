package leasegate.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/// Which gate a window belongs to, made impossible to miss: every gate's windows open with a wide
/// band in that gate's own colour, its icon large, and its name. Several gates run side by side and
/// their windows are otherwise alike; a human who approves out of habit should at least see, before
/// anything else, which gate is asking.
public record Brand(String product, String color, Image icon) {

    public Node band(String subtitle) {
        var view = new ImageView(icon);
        view.setFitWidth(64);
        view.setFitHeight(64);
        view.setPreserveRatio(true);
        var name = new Label(product.toUpperCase());
        name.setStyle("-fx-font-size: 26; -fx-font-weight: bold; -fx-text-fill: white;");
        var sub = new Label(subtitle);
        sub.setStyle("-fx-font-size: 12; -fx-text-fill: white;");
        var plate = new StackPane(view);
        plate.setPadding(new Insets(4));
        plate.setStyle("-fx-background-color: white; -fx-background-radius: 14;");
        var box = new HBox(14, plate, new VBox(2, name, sub));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 14, 10, 14));
        box.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 6;");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
}
