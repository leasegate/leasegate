package leasegate.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

/// Sends keys the way a stray keystroke arrives: into the window's own dispatch chain, at the control
/// that has the focus, and then at every control that could have it. Not OS keys: those go to whichever
/// window is in front, which may be the human's.
final class KeyProbe {

    static final List<KeyCode> STRAY = List.of(KeyCode.A, KeyCode.Y, KeyCode.O, KeyCode.G, KeyCode.E, KeyCode.R,
        KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.SPACE, KeyCode.ENTER, KeyCode.DELETE, KeyCode.BACK_SPACE);

    record Case(String name, Runnable open, BooleanSupplier acted, KeyCode accept, boolean arms, Runnable prime) {
        Case(String name, Runnable open, BooleanSupplier acted, KeyCode accept, boolean arms) { this(name, open, acted, accept, arms, () -> {}); }
    }

    static int failures;
    static final boolean VERBOSE = System.getenv("KEYPROBE_VERBOSE") != null;

    static <T> T fx(Supplier<T> s) {
        if (Platform.isFxApplicationThread()) return s.get();
        var f = new CompletableFuture<T>();
        Platform.runLater(() -> { try { f.complete(s.get()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.join();
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { throw new RuntimeException(e); } }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) failures++;
    }

    static List<Window> showing() { return fx(() -> new ArrayList<>(Window.getWindows().stream().filter(Window::isShowing).toList())); }

    /// Runs every case: stray keys while the window is arming, stray keys once it has settled and at
    /// every control that could hold the focus, then the accepting key, which must act.
    static void run(List<Case> cases) {
        for (var c : cases) one(c);
        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void one(Case c) {
        System.out.println("== " + c.name());
        var before = Set.copyOf(showing());
        Platform.runLater(c.open());
        Stage stage = null;
        for (var i = 0; i < 60 && stage == null; i++) {
            sleep(100);
            stage = showing().stream().filter(w -> !before.contains(w) && w instanceof Stage s && s.getWidth() > 10).map(w -> (Stage) w).findFirst().orElse(null);
        }
        if (stage == null) { check(c.name() + ": window opened", false); return; }
        var s = stage;
        sleep(250);
        c.prime().run();
        if (c.arms()) {
            var bad = strayAtFocus(s, c);
            check(c.name() + ": no stray key acts while it is still arming" + (bad == null ? "" : " (" + bad + ")"), bad == null);
            sleep(3500);
        }
        c.prime().run();
        var bad = strayEverywhere(s, c);
        check(c.name() + ": no letter, digit, Space or Enter acts, at the focus or on any control" + (bad == null ? "" : "  <- " + bad), bad == null);
        if (c.accept() != null && fx(s::isShowing) && !c.acted().getAsBoolean()) {
            c.prime().run();
            fx(() -> { fire(target(s), c.accept()); return null; });
            sleep(700);
            check(c.name() + ": " + c.accept() + " acts once it has settled", c.acted().getAsBoolean());
        }
        fx(() -> { s.close(); return null; });
        sleep(300);
    }

    static Node target(Stage s) {
        var scene = s.getScene();
        return scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
    }

    static String strayAtFocus(Stage s, Case c) {
        for (var k : STRAY) {
            if (!fx(s::isShowing)) return "window gone before " + k;
            var at = fx(() -> target(s));
            c.prime().run();
            fx(() -> { fire(at, k); return null; });
            sleep(60);
            if (VERBOSE) System.out.println("      " + k + " at " + describe(at) + " -> acted=" + c.acted().getAsBoolean() + " showing=" + fx(s::isShowing));
            if (c.acted().getAsBoolean() || !fx(s::isShowing)) return k + " at the focus (" + describe(at) + ")";
        }
        return null;
    }

    static String strayEverywhere(Stage s, Case c) {
        var seen = strayAtFocus(s, c);
        if (seen != null) return seen;
        var nodes = fx(() -> focusable(s.getScene()));
        for (var n : nodes) {
            for (var k : STRAY) {
                if (!fx(s::isShowing)) return "window gone before " + k + " on " + describe(n);
                c.prime().run();
                fx(() -> { n.requestFocus(); fire(n, k); return null; });
                sleep(60);
                if (c.acted().getAsBoolean() || !fx(s::isShowing)) return k + " on " + describe(n);
            }
        }
        var mnemonic = fx(() -> mnemonics(s.getScene()));
        return mnemonic.isEmpty() ? null : "a label still parses a mnemonic: " + mnemonic;
    }

    static List<Node> focusable(Scene scene) {
        var out = new ArrayList<Node>();
        for (var n : scene.getRoot().lookupAll("*")) if (n.isFocusTraversable() && !n.isDisabled() && n.isVisible()) out.add(n);
        return out;
    }

    static List<String> mnemonics(Scene scene) {
        var out = new ArrayList<String>();
        for (var n : scene.getRoot().lookupAll("*"))
            if (n instanceof Labeled l && l.isMnemonicParsing() && l.getText() != null && l.getText().contains("_")) out.add(l.getText());
        return out;
    }

    static String describe(Node n) {
        return n.getClass().getSimpleName() + (n instanceof Labeled l && l.getText() != null ? " \"" + l.getText() + "\"" : "");
    }

    static String text(KeyCode c) {
        return switch (c) { case SPACE -> " "; case ENTER -> "\r"; case DELETE, BACK_SPACE -> ""; default -> c.getChar().toLowerCase(); };
    }

    static void fire(Node target, KeyCode c) {
        var t = text(c);
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, t, c, false, false, false, false));
        if (!t.isEmpty() && c != KeyCode.ENTER) Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_TYPED, t, "", KeyCode.UNDEFINED, false, false, false, false));
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, KeyEvent.CHAR_UNDEFINED, t, c, false, false, false, false));
    }

    private KeyProbe() {}
}
