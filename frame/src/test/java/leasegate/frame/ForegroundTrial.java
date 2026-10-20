package leasegate.frame;

import javafx.application.Platform;
import javafx.scene.control.Label;

/// Two windows of one app with the SAME title: each must find its own native handle, the title must
/// come back unchanged, and at most one of them may glow (only the one Windows has in front).
/// Prints PASS or FAIL lines and exits.
public final class ForegroundTrial {

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> {});
        var frames = new GateFrame[2];
        Platform.runLater(() -> {
            var brand = new Brand("trialgate", "#2e7d32", GateIcons.leasegate());
            for (int i = 0; i < 2; i++) {
                frames[i] = new GateFrame(brand, "same title");
                frames[i].scene(new Label("window " + i));
                frames[i].stage.setX(100 + 420 * i);
                frames[i].stage.setY(100);
                frames[i].stage.show();
            }
        });
        Thread.sleep(2500);
        Platform.runLater(() -> {
            var a = frames[0]; var b = frames[1];
            check("handles found", a.hwnd != 0 && b.hwnd != 0);
            check("handles distinct", a.hwnd != b.hwnd);
            check("title restored", a.stage.getTitle().equals("TRIALGATE: same title") && b.stage.getTitle().equals(a.stage.getTitle()));
            check("at most one glows (live " + a.live + ", " + b.live + ")", !(a.live && b.live));
            check("a glowing window is the foreground one", (!a.live || Foreground.isFront(a.hwnd)) && (!b.live || Foreground.isFront(b.hwnd)));
            System.exit(0);
        });
    }

    static void check(String what, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + what); }
}
