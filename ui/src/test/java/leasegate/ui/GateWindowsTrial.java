package leasegate.ui;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;

import leasegate.frame.*;
import leasegate.core.access.*;
import leasegate.core.win.PeerIdentity;

/// PRP 06: the real gate windows, in GateFrame, with nothing behind them. Opens an attested and a
/// keyed approval and a trust request for this very process, screenshots them, then the passphrase
/// prompt, the unlock dialog and a notice one at a time over them.
/// Args: <shot-prefix> writes <prefix>-1.png .. -4.png and exits; --hold N keeps them open N seconds.
public final class GateWindowsTrial extends Application {

    record Items(Set<String> items, Integer requestedMinutes) implements Scope<Items> {
        @Override public boolean covers(Items r) { return items == null || r.items != null && items.containsAll(r.items); }
        @Override public Items narrowedTo(Items r) { return new Items(r.items != null ? r.items : items, r.requestedMinutes); }
        @Override public int narrowness() { return items == null ? 0 : 1; }
    }

    static final LeaseRules<Items> RULES = new LeaseRules<>() {
        @Override public String product() { return "gitgate"; }
        @Override public int keyedMaxMinutes(Items s) { return s.unscoped() ? 240 : s.items().contains("trash") ? 10 : 7 * 1440; }
        @Override public int riskBase(Items s) { return s.unscoped() ? 2 : 0; }
        @Override public List<ScopeLine> describe(Items s) { return List.of(summary(s)); }
        @Override public ScopeLine summary(Items s) { return s.unscoped() ? ScopeLine.wide("every repository") : ScopeLine.narrow("push to " + String.join(", ", s.items())); }
        @Override public void writeScope(ObjectNode n, Items s) {}
        @Override public Items readScope(JsonNode n) { return null; }
        @Override public Items stored(Items s) { return new Items(s.items(), null); }
    };

    static final Brand GIT = new Brand("gitgate", "#2e7d32", GateIcon.image("#2e7d32"));

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage ignored) throws Exception {
        Platform.setImplicitExit(false);
        var gate = new PassphraseGate(Files.createTempDirectory("lg").resolve("p.json"));
        gate.setInitialPassphrase("trial1");
        var pid = ProcessHandle.current().pid();
        var tree = ProcessTree.snapshot();
        var chain = tree.chainFrom(pid);
        var me = PeerIdentity.resolve(pid);
        var scope = new Items(Set.of("example-org/example-repo"), 60);

        var attested = new ApprovalWindow<>(new Approval.Request<>(Approval.Kind.ATTESTED, me, chain, tree, scope, "git", null, null), gate, RULES, GIT, d -> {});
        attested.show();
        attested.stage.setX(20); attested.stage.setY(20);
        var trust = new TrustWindow<>(new TrustApproval.Request<>(me, chain.getFirst(), scope, "git"), gate, RULES, GIT, d -> {});
        trust.show();
        trust.stage.setX(720); trust.stage.setY(20);
        var capped = new ApprovalWindow<>(new Approval.Request<>(Approval.Kind.KEYED, me, chain, tree, new Items(Set.of("trash"), 10), "bwgate-cli", "SHA256:example", "move items to the trash"), gate, RULES, GIT, d -> {});
        capped.show();
        capped.stage.setX(1100); capped.stage.setY(20);

        var raw = getParameters().getRaw();
        if (raw.getFirst().equals("--mouse")) { mouse(attested, trust); return; }
        if (raw.size() == 2 && raw.get(0).equals("--hold")) { later(Integer.parseInt(raw.get(1)), Platform::exit); return; }
        var prefix = raw.getFirst();
        later(3, () -> {
            shot(prefix + "-1.png");
            Platform.runLater(() -> PassphrasePrompt.confirmOnce(gate, GIT, "confirm a wide approval", "This request reaches everything this gate guards. Type the passphrase to approve it anyway."));
            later(2, () -> {
                shot(prefix + "-2.png");
                close("confirm a wide approval");
                Platform.runLater(() -> PassphraseUnlockDialog.unlockOrSetup(new PassphraseGate(tempStore()), GIT));
                later(2, () -> {
                    shot(prefix + "-3.png");
                    Platform.runLater(() -> GateFrame.tell(GIT, "nothing to extend", "That approval had already ended, so there was nothing to extend. The next request will ask again."));
                    later(2, () -> { shot(prefix + "-4.png"); System.exit(0); });
                });
            });
        });
    }

    /// Drags the trust window by its band, then clicks the approval window's ✕, and checks what happened.
    static void mouse(ApprovalWindow<Items> attested, TrustWindow<Items> trust) {
        var robot = new javafx.scene.robot.Robot();
        later(3, () -> {
            var t = trust.stage;
            double x0 = t.getX(), y0 = t.getY();
            int sx = (int) (t.getX() + 200), sy = (int) (t.getY() + 30);
            // A real drag runs the OS move loop on the FX thread, so the mouse is driven from another thread.
            Thread.ofPlatform().start(() -> {
                try {
                    var awt = new java.awt.Robot();
                    awt.mouseMove(sx, sy); Thread.sleep(200);
                    awt.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK); Thread.sleep(200);
                    for (int i = 1; i <= 12; i++) { awt.mouseMove(sx - i * 10, sy + i * 5); Thread.sleep(30); }
                    Thread.sleep(200);
                    awt.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                } catch (Exception e) { e.printStackTrace(); }
            });
            later(2.5, () -> {
                System.out.printf("drag band: moved by %.0f,%.0f (expected about -120,60)%n", t.getX() - x0, t.getY() - y0);
                var a = attested.stage;
                robot.mouseMove(a.getX() + a.getWidth() - 20, a.getY() + 16);
                robot.mouseClick(javafx.scene.input.MouseButton.PRIMARY);
                later(0.8, () -> {
                    System.out.println("close button: approval decided=" + attested.decided + ", window showing=" + a.isShowing());
                    System.exit(0);
                });
            });
        });
    }

    static void later(double seconds, Runnable r) {
        var p = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(seconds));
        p.setOnFinished(e -> r.run());
        p.play();
    }

    static void capture(List<Stage> stages, String file) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = 0, maxY = 0;
        for (var s : stages) {
            minX = Math.min(minX, s.getX()); minY = Math.min(minY, s.getY());
            maxX = Math.max(maxX, s.getX() + s.getWidth()); maxY = Math.max(maxY, s.getY() + s.getHeight());
        }
        var img = new javafx.scene.robot.Robot().getScreenCapture(null, new javafx.geometry.Rectangle2D(minX - 14, minY - 14, maxX - minX + 28, maxY - minY + 28));
        var pr = img.getPixelReader();
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
        try { javax.imageio.ImageIO.write(bi, "png", new java.io.File(file)); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static java.nio.file.Path tempStore() {
        try { return Files.createTempDirectory("lg").resolve("p.json"); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static void close(String titleEnd) {
        Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getTitle() != null && s.getTitle().endsWith(titleEnd))
            .toList().forEach(w -> ((Stage) w).close());
    }

    static void shot(String file) {
        var stages = Window.getWindows().stream().filter(w -> w instanceof Stage s && s.isShowing() && s.getWidth() > 10)
            .map(w -> (Stage) w).toList();
        capture(stages, file);
    }
}
