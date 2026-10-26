package leasegate.frame;

import javafx.scene.effect.*;
import javafx.scene.paint.Color;

/// A glow is the light of the app's colour, not the colour itself: the same hue at full brightness,
/// so a dark brown glows golden and a dark green glows bright green. A tight bright rim close to
/// the edge, a wide soft halo further out, and a border running from an almost-white tint at the
/// top to the bright hue at the bottom, as light falls on glass.
record Glow(Color core, Color outer, Color tint) {

    /// Below this chroma (largest minus smallest channel, 0 to 1) a brand colour is black, white or grey, and its hue is noise
    /// (#16181D has a hue of 225 from a blue channel 7 higher than the red): clamping the saturation up would paint a saturated
    /// blue rim around a black window. Chroma, not HSB saturation: saturation is relative to brightness, so a near-black reads 0.24.
    static final double ACHROMATIC = 0.1;

    static Glow of(String brandColor) {
        var c = Color.web(brandColor);
        double h = c.getHue(), s = c.getSaturation();
        double chroma = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue())) - Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
        if (chroma < ACHROMATIC) return new Glow(Color.gray(0.62, 0.9), Color.gray(0.45, 0.5), Color.gray(0.86));
        return new Glow(Color.hsb(h, clamp(s, 0.55, 1), 1, 0.9), Color.hsb(h, clamp(s * 0.85, 0.45, 0.9), 1, 0.5),
            Color.hsb(h, clamp(s * 0.3, 0.1, 0.35), 1));
    }

    Effect halo() {
        var rim = new DropShadow(BlurType.GAUSSIAN, core, 12, 0.35, 0, 0);
        var far = new DropShadow(BlurType.GAUSSIAN, outer, 48, 0, 0, 0);
        far.setInput(rim);
        return far;
    }

    Effect inner() { return new InnerShadow(BlurType.GAUSSIAN, core.deriveColor(0, 1, 1, 0.55), 16, 0.2, 0, 0); }

    String rim() { return "linear-gradient(to bottom, " + hex(tint) + ", " + hex(core) + ")"; }

    /// The band as glass: the brand colour as a top-to-bottom gradient.
    static String glass(String brandColor) {
        var c = Color.web(brandColor);
        return "linear-gradient(to bottom, " + hex(c.deriveColor(0, 0.85, 1.35, 1)) + ", " + hex(c.deriveColor(0, 1.05, 0.85, 1)) + ")";
    }

    static final String SHEEN = "-fx-background-color: linear-gradient(to bottom, rgba(255,255,255,0.38) 0%, rgba(255,255,255,0.10) 48%, rgba(255,255,255,0) 52%);"
        + " -fx-border-color: rgba(255,255,255,0.55) transparent transparent transparent; -fx-border-width: 1 0 0 0;";

    static final Effect IDLE_SHADOW = new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.30), 22, 0, 0, 6);

    static ColorAdjust grey() { return new ColorAdjust(0, -1, 0.05, -0.15); }

    static String hex(Color c) {
        return "#%02x%02x%02x".formatted((int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
