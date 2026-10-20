package leasegate.frame;

import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import javafx.scene.image.Image;

/// A gate's icons, light and dark, shipped as PNG resources beside `owner`:
/// `<name>-{16,24,32,48,256}.png` and `<name>-dark-{16,…}.png`. The 16 is drawn pixel by pixel and is
/// never replaced by a scaled-down larger one. Use the dark set when the taskbar is dark (TaskbarTheme).
public record GateIcons(Class<?> owner, String name) {

    static final int[] TRAY = {16, 24, 32}, WINDOW = {16, 32, 48, 256};

    /// The icons of the leasegate library itself, for trials and anything without a set of its own.
    public static GateIcons leasegate() { return new GateIcons(GateIcons.class, "leasegate"); }

    /// The tray picture: 16, 24 and 32 px in one image, so Windows draws the one that matches the
    /// display scaling (100% 16, 125% and 150% 24, 200% 32) rather than a blurred rescale of one.
    public java.awt.Image tray(boolean dark) {
        var variants = new ArrayList<BufferedImage>();
        for (int s : TRAY) variants.add(awt(dark, s));
        return new BaseMultiResolutionImage(variants.toArray(new java.awt.Image[0]));
    }

    /// For Stage.getIcons(): JavaFX hands Windows the size each place asks for.
    public List<Image> window(boolean dark) {
        var images = new ArrayList<Image>();
        for (int s : WINDOW) images.add(fx(dark, s));
        return images;
    }

    /// The large light picture, shown on the white plate in a window's band.
    public Image header() { return fx(false, 256); }

    String resource(boolean dark, int size) { return name + (dark ? "-dark-" : "-") + size + ".png"; }

    Image fx(boolean dark, int size) {
        try (var in = owner.getResourceAsStream(resource(dark, size))) {
            if (in == null) throw new IllegalStateException("icon missing beside " + owner.getName() + ": " + resource(dark, size));
            return new Image(in);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    BufferedImage awt(boolean dark, int size) {
        try (var in = owner.getResourceAsStream(resource(dark, size))) {
            if (in == null) throw new IllegalStateException("icon missing beside " + owner.getName() + ": " + resource(dark, size));
            return ImageIO.read(in);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
}
