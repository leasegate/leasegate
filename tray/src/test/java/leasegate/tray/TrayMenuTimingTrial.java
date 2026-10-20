package leasegate.tray;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import javafx.application.Platform;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.WritableImage;

/// How long the menu takes to appear: from the call to the first frame drawn (the end of the first
/// pulse after show), the first open and repeated ones. No tray icon is needed.
/// java -cp <cp> leasegate.tray.TrayMenuTimingTrial
public class TrayMenuTimingTrial {

    public static void main(String[] a) throws Exception {
        Platform.startup(() -> {});
        Platform.setImplicitExit(false);
        var items = (java.util.function.Supplier<List<MenuItem>>) () -> List.of(item("Open the window"), item("Who has access…"), new SeparatorMenuItem(),
            item("Start the thing"), new SeparatorMenuItem(), item("Blanket mode for testing (up to 4 h)…"), item("Revoke every approval now"),
            new SeparatorMenuItem(), item("Check for updates…"), item("About"), new SeparatorMenuItem(), item("Quit"));
        var menu = new TrayMenu(new WritableImage(64, 64), "trial", "#2e6da8", () -> "3 key(s) · 1 approval(s)", items);
        var t0 = System.nanoTime();
        var warm = new CountDownLatch(1);
        Platform.runLater(() -> { menu.prewarm(); Platform.runLater(warm::countDown); });
        warm.await();
        System.out.printf("prewarm (at install): %.1f ms%n", (System.nanoTime() - t0) / 1e6);
        Thread.sleep(300);
        for (var i = 0; i < 8; i++) {
            var drawn = new CountDownLatch(1);
            var t = new long[3];
            Platform.runLater(() -> {
                t[0] = System.nanoTime();
                menu.show(1500, 900);
                t[1] = System.nanoTime();
                menu.stage.getScene().addPostLayoutPulseListener(new Runnable() {
                    public void run() { t[2] = System.nanoTime(); menu.stage.getScene().removePostLayoutPulseListener(this); drawn.countDown(); }
                });
            });
            drawn.await();
            System.out.printf("open %d: show() %.1f ms, first frame %.1f ms%n", i + 1, (t[1] - t[0]) / 1e6, (t[2] - t[0]) / 1e6);
            Thread.sleep(400);
            Platform.runLater(menu::close);
            Thread.sleep(400);
        }
        System.exit(0);
    }

    static MenuItem item(String text) { return new MenuItem(text); }
}
