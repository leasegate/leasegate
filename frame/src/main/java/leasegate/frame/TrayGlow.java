package leasegate.frame;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.ArrayList;

/// The tray icon in the gate's three states, told apart at a glance at 16 px:
///
/// - LOCKED: the icon greyed out. Nothing is reachable through the gate.
/// - ACTIVE: unlocked, granting per request: full colour with a backlight behind the shape, white on a
///   dark taskbar and the light of the gate's colour on a light one.
/// - OPEN: blanket mode, every request passes: ACTIVE plus a laser running round the edge, `FRAMES`
///   pictures shown `FRAME_MS` apart.
///
/// The cue that carries the state is the same on every icon (grey or colour, still or moving), so it
/// holds for icons that fill their whole square and leave no room for a halo.
public final class TrayGlow {

    public enum State { LOCKED, ACTIVE, OPEN }

    public static final int FRAMES = 12, FRAME_MS = 110;

    /// Every resolution variant of `icon` in `state`; `frame` (0..FRAMES-1) places the laser when OPEN.
    public static java.awt.Image picture(java.awt.Image icon, String brandColor, State state, boolean dark, int frame) {
        if (!(icon instanceof MultiResolutionImage mri)) return icon;
        var glow = awt(Glow.of(brandColor).core());
        var variants = new ArrayList<java.awt.Image>();
        for (var v : mri.getResolutionVariants()) {
            var src = (BufferedImage) v;
            variants.add(switch (state) {
                case LOCKED -> grey(src, dark);
                case ACTIVE -> backlit(src, glow, 1.0);
                case OPEN -> laser(backlit(src, glow, 1.0), dark, frame);
            });
        }
        return new BaseMultiResolutionImage(variants.toArray(new java.awt.Image[0]));
    }

    static Color awt(javafx.scene.paint.Color c) {
        return new Color((float) c.getRed(), (float) c.getGreen(), (float) c.getBlue());
    }

    /// Grey, a little faded, still the gate's shape: dark grey would vanish on a dark taskbar, so the
    /// grey is lifted there.
    static BufferedImage grey(BufferedImage src, boolean dark) {
        int w = src.getWidth(), h = src.getHeight();
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = src.getRGB(x, y), a = p >>> 24;
            if (a == 0) continue;
            int l = (int) Math.round(0.30 * ((p >> 16) & 255) + 0.59 * ((p >> 8) & 255) + 0.11 * (p & 255));
            l = dark ? 90 + l * 110 / 255 : 70 + l * 120 / 255;
            out.setRGB(x, y, ((a * 3 / 4) << 24) | (l << 16) | (l << 8) | l);
        }
        return out;
    }

    /// A light behind the shape: the silhouette, widened and blurred, in `light`, under the icon.
    static BufferedImage backlit(BufferedImage src, Color light, double strength) {
        int w = src.getWidth(), h = src.getHeight();
        var a = new float[w * h];
        for (int i = 0; i < a.length; i++) a[i] = (src.getRGB(i % w, i / w) >>> 24) / 255f;
        int r = Math.max(1, w / 14);
        var halo = blur(dilate(a, w, h, r), w, h, r);
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float lr = light.getRed(), lg = light.getGreen(), lb = light.getBlue();
        for (int i = 0; i < a.length; i++) {
            int p = src.getRGB(i % w, i / w);
            float sa = a[i], ha = (float) Math.min(1, halo[i] * 1.6 * strength) * (1 - sa);
            float oa = sa + ha;
            if (oa <= 0) continue;
            float or = (((p >> 16) & 255) * sa + lr * ha) / oa, og = (((p >> 8) & 255) * sa + lg * ha) / oa, ob = ((p & 255) * sa + lb * ha) / oa;
            out.setRGB(i % w, i / w, (Math.round(Math.min(1, oa) * 255) << 24) | (Math.round(or) << 16) | (Math.round(og) << 8) | Math.round(ob));
        }
        return out;
    }

    static final Color AMBER = new Color(0xF2, 0xA9, 0x3B), DEEP_AMBER = new Color(0xE0, 0x6A, 0x00);

    /// Two lasers chasing round the edge of the square, opposite each other: a white head and a tail in
    /// the lease amber, which no gate icon uses for its body, so the motion stands out on every one.
    static BufferedImage laser(BufferedImage src, boolean dark, int frame) {
        int s = src.getWidth();
        var out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.drawImage(src, 0, 0, null);
        float sw = Math.max(2.2f, s / 7f), inset = sw / 2;
        var edge = new RoundRectangle2D.Float(inset, inset, s - sw, s - sw, s / 3f, s / 3f);
        var points = new ArrayList<Point2D>();
        var it = edge.getPathIterator(null, 0.25);
        var c = new float[6];
        for (; !it.isDone(); it.next()) if (it.currentSegment(c) != PathIterator.SEG_CLOSE) points.add(new Point2D.Float(c[0], c[1]));
        int n = points.size(), tail = Math.max(3, n / 4);
        var tailColor = dark ? AMBER : DEEP_AMBER;
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int beam = 0; beam < 2; beam++) {
            int head = (int) ((long) frame * n / FRAMES) + beam * n / 2;
            for (int k = tail; k >= 1; k--) {
                var p0 = points.get(((head - k) % n + n) % n);
                var p1 = points.get(((head - k + 1) % n + n) % n);
                float t = 1 - (float) k / tail;   // 0 at the tail's end, 1 at the head
                var col = mix(tailColor, Color.WHITE, t * t * t);
                g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), Math.round(255 * (0.35f + 0.65f * t))));
                g.draw(new Line2D.Double(p0, p1));
            }
        }
        g.dispose();
        return out;
    }

    static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
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
            float s = 0;
            for (int d = -r; d <= r; d++) {
                int xx = horizontal ? x + d : x, yy = horizontal ? y : y + d;
                if (xx >= 0 && yy >= 0 && xx < w && yy < h) s += a[yy * w + xx];
            }
            out[y * w + x] = s / (2 * r + 1);
        }
        return out;
    }

    private TrayGlow() {}
}
