package leasegate.frame;

import java.io.File;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/// SecretField with a real key: types into two fields, then drives F9 -- a short press shows and
/// hides, a long hold keeps it shown, a further press hides -- and checks each state. Screenshots
/// the kept state to <arg0>.png when a path is given.
public final class SecretFieldTrial extends Application {

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage ignored) {
        var brand = new Brand("gitgate", "#2e7d32", GateIcon.image("#2e7d32"));
        var a = new SecretField();
        var b = new SecretField();
        a.setText("correct horse");
        b.setText("correct hrose");
        var body = new VBox(10, Parts.small("First run: set the passphrase, twice.", "#333"), a, b);
        body.setPadding(new Insets(16));
        body.setPrefWidth(420);
        var frame = new GateFrame(brand, "first run: set the passphrase");
        frame.scene(body);
        frame.stage.show();
        var shot = getParameters().getRaw().isEmpty() ? null : getParameters().getRaw().getFirst();
        if ("--hold".equals(shot)) { a.clear(); b.clear(); a.requestFocus(); later(600, () -> System.exit(0)); return; }
        later(1.5, () -> {
            a.requestFocus();
            System.out.println("window has OS focus: " + frame.stage.isFocused());
            check("start: hidden", !a.revealed && !b.revealed);
            key(frame, javafx.scene.input.KeyEvent.KEY_PRESSED);
            later(0.3, () -> {
                check("short press: both shown while held", a.revealed && b.revealed && a.shown.isVisible());
                key(frame, javafx.scene.input.KeyEvent.KEY_RELEASED);
                later(0.3, () -> {
                    check("short press released: hidden again", !a.revealed && !a.kept);
                    key(frame, javafx.scene.input.KeyEvent.KEY_PRESSED);
                    later(1.6, () -> {
                        key(frame, javafx.scene.input.KeyEvent.KEY_RELEASED);
                        later(0.3, () -> {
                            check("long hold released: still shown (kept)", a.revealed && a.kept && b.kept);
                            check("focus stayed in a field", a.input().isFocused());
                            if (shot != null) capture(frame.stage, shot);
                            key(frame, javafx.scene.input.KeyEvent.KEY_PRESSED);
                            later(0.15, () -> key(frame, javafx.scene.input.KeyEvent.KEY_RELEASED));
                            later(0.45, () -> {
                                check("press again: hidden", !a.revealed && !a.kept);
                                check("text untouched", a.getText().equals("correct horse"));
                                System.exit(failures == 0 ? 0 : 1);
                            });
                        });
                    });
                });
            });
        });
    }

    /// F9 into the window's own dispatch chain, so the scene filters run exactly as for a real key.
    /// Not an OS-level key: that goes to whichever window is in front, which may be the human's.
    static void key(GateFrame f, javafx.event.EventType<javafx.scene.input.KeyEvent> type) {
        javafx.event.Event.fireEvent(f.stage.getScene().getFocusOwner() != null ? f.stage.getScene().getFocusOwner() : f.stage.getScene().getRoot(),
            new javafx.scene.input.KeyEvent(type, "", "", KeyCode.F9, false, false, false, false));
    }

    static int failures;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) failures++;
    }

    static void later(double seconds, Runnable r) {
        var p = new PauseTransition(Duration.seconds(seconds));
        p.setOnFinished(e -> r.run());
        p.play();
    }

    static void capture(Stage s, String file) {
        var img = new javafx.scene.robot.Robot().getScreenCapture(null, new Rectangle2D(s.getX(), s.getY(), s.getWidth(), s.getHeight()));
        var pr = img.getPixelReader();
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
        try { javax.imageio.ImageIO.write(bi, "png", new File(file)); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
