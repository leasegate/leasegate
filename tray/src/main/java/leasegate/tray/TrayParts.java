package leasegate.tray;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/// The small pieces TrayMenu is drawn with.
final class TrayParts {

    static Region separator() {
        var r = new Region();
        r.setMinHeight(1);
        r.setMaxHeight(1);
        r.setStyle("-fx-background-color: #e4e4e4;");
        VBox.setMargin(r, new Insets(4, 10, 4, 10));
        return r;
    }

    /// The header as glass: the colour as a top-to-bottom gradient.
    static String glass(String color) {
        var c = Color.web(color);
        return "linear-gradient(to bottom, " + rgba(c.deriveColor(0, 0.85, 1.35, 1), 1) + ", " + rgba(c.deriveColor(0, 1.05, 0.85, 1), 1) + ")";
    }

    static String rgba(Color c, double alpha) {
        return "rgba(%d,%d,%d,%s)".formatted(Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255), alpha);
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(v, Math.max(lo, hi))); }

    /// A transparent utility window that owns the menu, so the menu has no taskbar button.
    static Stage hiddenOwner() {
        var s = new Stage(StageStyle.UTILITY);
        s.setOpacity(0);
        s.setWidth(1);
        s.setHeight(1);
        s.setX(-32000);
        s.setY(-32000);
        s.setScene(new Scene(new Region()));
        s.show();
        return s;
    }

    private TrayParts() {}
}
