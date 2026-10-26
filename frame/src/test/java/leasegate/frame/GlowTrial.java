package leasegate.frame;

import javafx.scene.paint.Color;

/// The rim of a black, white or grey brand must be grey, not a saturated colour made from noise in the hue, and a
/// coloured brand must keep its hue. Prints PASS or FAIL lines; exit code 1 on any FAIL.
public final class GlowTrial {

    static int failed;

    public static void main(String[] args) {
        for (var brand : new String[] {"#16181D", "#000000", "#808080", "#ffffff"}) {
            var g = Glow.of(brand);
            check(brand + " core is grey (saturation " + g.core().getSaturation() + ")", g.core().getSaturation() < 0.05);
            check(brand + " outer is grey", g.outer().getSaturation() < 0.05);
            check(brand + " tint is grey", g.tint().getSaturation() < 0.05);
        }
        var green = Glow.of("#2e7d32");
        check("green keeps its hue (" + green.core().getHue() + ")", Math.abs(green.core().getHue() - Color.web("#2e7d32").getHue()) < 1);
        check("green is still saturated", green.core().getSaturation() > 0.5);
        System.exit(failed == 0 ? 0 : 1);
    }

    static void check(String what, boolean ok) {
        if (!ok) failed++;
        System.out.println((ok ? "PASS " : "FAIL ") + what);
    }
}
