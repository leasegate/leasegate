package leasegate.frame;

import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.ArrayList;

/// The tray icon lit up while the gate is open, in the light of the gate's colour (see Glow): a soft
/// halo in the empty pixels around the shape and a bright rim just inside its edge, the same idea as
/// the halo and inner glow on a gate's windows. A 16 px icon has almost no room outside its shape, so
/// the inner rim is what carries the state on the icons that fill their square.
public final class TrayGlow {

    /// Locked shows the icon as drawn; open glows; blanket mode glows harder (and pulses, see TrayShell).
    public static final double OPEN = 0.7, BLANKET = 1.0, PULSE = 1.25;

    /// Every resolution variant of `icon`, lit at `strength` (0 = as drawn).
    public static java.awt.Image lit(java.awt.Image icon, String brandColor, double strength) {
        if (strength <= 0 || !(icon instanceof MultiResolutionImage mri)) return icon;
        var c = Glow.of(brandColor).core();
        int rgb = ((int) Math.round(c.getRed() * 255) << 16) | ((int) Math.round(c.getGreen() * 255) << 8) | (int) Math.round(c.getBlue() * 255);
        var variants = new ArrayList<java.awt.Image>();
        for (var v : mri.getResolutionVariants()) variants.add(lit((BufferedImage) v, rgb, strength));
        return new BaseMultiResolutionImage(variants.toArray(new java.awt.Image[0]));
    }

    static BufferedImage lit(BufferedImage src, int rgb, double strength) {
        int w = src.getWidth(), h = src.getHeight();
        var a = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) a[y * w + x] = (src.getRGB(x, y) >>> 24) / 255f;
        int r = Math.max(1, w / 10);
        var spread = blur(dilate(a, w, h, r), w, h, r);   // reaches out from the shape
        float gr = (rgb >> 16) & 255, gg = (rgb >> 8) & 255, gb = rgb & 255;
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < a.length; i++) {
            int p = src.getRGB(i % w, i / w);
            float sa = a[i], sr = (p >> 16) & 255, sg = (p >> 8) & 255, sb = p & 255;
            // inner rim: only the pixels on the shape's outer edge, so the drawing inside stays as drawn
            float rim = edge(a, w, h, i) ? (float) Math.min(0.6, sa * 0.45 * strength) : 0;
            sr += (gr - sr) * rim; sg += (gg - sg) * rim; sb += (gb - sb) * rim;
            // outer halo under the shape
            float ha = (float) Math.min(1, spread[i] * 0.85 * strength) * (1 - sa);
            float oa = sa + ha;
            float or = oa == 0 ? 0 : (sr * sa + gr * ha) / oa, og = oa == 0 ? 0 : (sg * sa + gg * ha) / oa, ob = oa == 0 ? 0 : (sb * sa + gb * ha) / oa;
            out.setRGB(i % w, i / w, (Math.round(Math.min(1, oa) * 255) << 24) | (Math.round(or) << 16) | (Math.round(og) << 8) | Math.round(ob));
        }
        return out;
    }

    /// An opaque pixel next to an empty one (or the canvas edge).
    static boolean edge(float[] a, int w, int h, int i) {
        if (a[i] < 0.5f) return false;
        int x = i % w, y = i / w;
        return x == 0 || y == 0 || x == w - 1 || y == h - 1
            || a[i - 1] < 0.5f || a[i + 1] < 0.5f || a[i - w] < 0.5f || a[i + w] < 0.5f;
    }

    static float[] dilate(float[] a, int w, int h, int r) {
        var out = new float[a.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float m = 0;
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && yy >= 0 && xx < w && yy < h && dx * dx + dy * dy <= r * r) m = Math.max(m, a[yy * w + xx]);
            }
            out[y * w + x] = m;
        }
        return out;
    }

    /// Three box passes, close enough to a gaussian at these sizes.
    static float[] blur(float[] a, int w, int h, int r) {
        var t = a.clone();
        for (int pass = 0; pass < 3; pass++) { t = box(t, w, h, r, true); t = box(t, w, h, r, false); }
        return t;
    }

    static float[] box(float[] a, int w, int h, int r, boolean horizontal) {
        var out = new float[a.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float s = 0; int n = 0;
            for (int d = -r; d <= r; d++) {
                int xx = horizontal ? x + d : x, yy = horizontal ? y : y + d;
                if (xx >= 0 && yy >= 0 && xx < w && yy < h) s += a[yy * w + xx];
                n++;   // outside counts as empty, so the edge of the canvas fades too
            }
            out[y * w + x] = s / n;
        }
        return out;
    }

    private TrayGlow() {}
}
