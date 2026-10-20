package leasegate.frame;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;

/// A small drawn padlock, not a stock teacup icon (per the javafx guidelines: distinguish
/// projects rather than leave every tray app looking the same). Drawn with Canvas rather than an
/// SVG/PNG asset, so there is nothing to ship.
public final class GateIcon {

    public static Image image() { return image("#2e6da8"); }

    /// A gate's own tray picture, shipped as a resource beside `owner`; the drawn padlock if absent.
    public static Image image(Class<?> owner, String resource, String fallbackColor) {
        try (var in = owner.getResourceAsStream(resource)) {
            return in == null ? image(fallbackColor) : new Image(in);
        } catch (Exception e) { return image(fallbackColor); }
    }

    /// The padlock in another colour, so two gates side by side in the tray can be told apart.
    public static Image image(String color) {
        var size = 32;
        var canvas = new Canvas(size, size);
        new Scene(new Group(canvas));   // forces layout/CSS so snapshot() works off-screen

        var g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, size, size);
        g.setStroke(Color.web(color));
        g.setLineWidth(3.5);
        g.strokeArc(8, 4, 16, 16, 0, 180, ArcType.OPEN);
        g.setFill(Color.web(color));
        g.fillRoundRect(6, 14, 20, 14, 5, 5);
        g.setFill(Color.WHITE);
        g.fillOval(14, 19, 4, 4);

        var params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return viaPng(canvas.snapshot(params, null));
    }

    /// Windows silently ignores a WritableImage as a window icon (the title bar and taskbar show
    /// the generic glyph), so the drawing is passed through an in-memory PNG and loaded back.
    static Image viaPng(Image drawn) {
        int w = (int) drawn.getWidth(), h = (int) drawn.getHeight();
        var pixels = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var reader = drawn.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) pixels.setRGB(x, y, reader.getArgb(x, y));
        var bytes = new ByteArrayOutputStream();
        try { ImageIO.write(pixels, "png", bytes); } catch (IOException e) { return drawn; }
        return new Image(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private GateIcon() {}
}
