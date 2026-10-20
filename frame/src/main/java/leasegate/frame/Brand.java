package leasegate.frame;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/// Which gate a window belongs to, made impossible to miss: every gate's windows are framed by a
/// band in that gate's own colour, with its icon and its name (see GateFrame). Several gates run
/// side by side and their windows are otherwise alike; a human who approves out of habit should at
/// least see, before anything else, which gate is asking.
public record Brand(String product, String color, GateIcons icons) {

    /// The large light picture, for the band, the logo and the tray menu (all on a light plate).
    public Image icon() { return icons.header(); }

    /// The gate's big mark for a home or about window: its picture, its name in its colour, and a
    /// line saying what it guards, so two gates' windows are told apart at a glance.
    public Node logo(double size, String tagline) {
        var view = new ImageView(icon());
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        var name = new Label(product);
        name.setStyle("-fx-font-size: " + Math.round(size * 0.34) + "; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        var box = new VBox(2, name);
        if (tagline != null && !tagline.isBlank()) {
            var line = new Label(tagline);
            line.setWrapText(true);
            line.setStyle("-fx-font-size: 13; -fx-text-fill: #555;");
            box.getChildren().add(line);
        }
        box.setAlignment(Pos.CENTER_LEFT);
        var row = new HBox(18, view, box);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /// The band's content: icon on a white plate, the gate's name, and what this window is.
    /// GateFrame paints the colour behind it and makes it the title bar.
    Node header(String subtitle) {
        var view = new ImageView(icon());
        view.setFitWidth(40);
        view.setFitHeight(40);
        view.setPreserveRatio(true);
        var plate = new StackPane(view);
        plate.setPadding(new Insets(3));
        plate.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        var name = new Label(product.toUpperCase());
        name.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: white;");
        var sub = new Label(subtitle);
        sub.setStyle("-fx-font-size: 11; -fx-text-fill: white;");
        var box = new HBox(12, plate, new VBox(0, name, sub));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8, 12, 8, 12));
        return box;
    }
}
