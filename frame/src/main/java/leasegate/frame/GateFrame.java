package leasegate.frame;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/// The frame every window of an app is built in. There is no platform title bar: the app's colour
/// band is the title bar (drag it to move the window), so which app is asking is the first thing
/// seen. While the window has focus it glows in the light of the app's colour; without focus the
/// whole window -- band, buttons, text -- goes grey, so the live window stands out among several.
/// The only header button is close: a Windows ✕ on the right, or mac traffic lights on the left
/// above the band. Closing is the window's own cancel. Every window has a taskbar button, so it is
/// in Alt+Tab (without the button, Windows 11 leaves it out of Alt+Tab as well).
///
/// Two styles, chosen per app with `styles(dialogs, resizable)`:
/// GLOW draws the whole frame itself (rounded, a halo around it); a resizable GLOW window is
/// resized from its edges by our own code, and Windows' snap does not apply to it.
/// NATIVE keeps the platform frame (its shadow, snap and edge resizing) and adds a luminous rim.
public final class GateFrame {

    public enum Style {
        GLOW, NATIVE;

        /// From a settings value, "glow" or "native" in any case; anything else (or nothing) is GLOW.
        public static Style of(String name) { return "native".equalsIgnoreCase(name == null ? "" : name.trim()) ? NATIVE : GLOW; }
    }

    static Style dialogStyle = Style.GLOW, resizableStyle = Style.GLOW;

    /// Which style this app's windows use: one for fixed-size dialogs, one for resizable windows.
    /// Set once at startup, before any window is made, typically from the app's own settings.
    public static void styles(Style dialogs, Style resizable) { dialogStyle = dialogs; resizableStyle = resizable; }

    static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
    static final double HALO = 36, EDGE = 6, RADIUS = 12;

    public final Stage stage;
    final Brand brand;
    final String subtitle;
    final boolean resizable;
    final Style style;
    boolean closable = true, taskbar = true;
    /// Whether the keyboard really types into this window (see Foreground), which is what the glow shows.
    boolean live, painted;
    long hwnd;
    Runnable onClose;

    /// A fixed-size window.
    public GateFrame(Brand brand, String subtitle) { this(brand, subtitle, false); }

    /// A window the human can resize (a list, a table).
    public static GateFrame resizable(Brand brand, String subtitle) { return new GateFrame(brand, subtitle, true); }

    GateFrame(Brand brand, String subtitle, boolean resizable) {
        this.brand = brand;
        this.subtitle = subtitle;
        this.resizable = resizable;
        this.style = resizable ? resizableStyle : dialogStyle;
        this.stage = new Stage(style == Style.GLOW ? StageStyle.TRANSPARENT : StageStyle.EXTENDED);
        this.onClose = stage::close;
        stage.setResizable(resizable);
        stage.setAlwaysOnTop(true);
    }

    /// Blocks every other window of the app until closed (for showAndWait).
    public GateFrame modal() { stage.initModality(Modality.APPLICATION_MODAL); return this; }

    /// Gives the window a taskbar button. This is the default; kept so existing callers still compile.
    public GateFrame inTaskbar() { taskbar = true; return this; }

    /// Removes the taskbar button (ITaskbarList::DeleteTab). On Windows 11 this also takes the window
    /// out of Alt+Tab, so a prompt that goes behind something is lost: use only for windows nobody
    /// needs to switch back to.
    public GateFrame notInTaskbar() { taskbar = false; return this; }

    public GateFrame notOnTop() { stage.setAlwaysOnTop(false); return this; }

    /// What the close button and Alt+F4 do. Default: close the window.
    public GateFrame onClose(Runnable r) { onClose = r; return this; }

    /// No close button, and Alt+F4 does nothing: the window ends only through its own buttons.
    public GateFrame notClosable() { closable = false; return this; }

    /// Puts `body` under the band and returns the scene, for key handlers. Call once, then show.
    public Scene scene(Node body) {
        VBox.setVgrow(body, Priority.ALWAYS);
        var label = brand.header(subtitle);
        Node left = MAC && closable ? new VBox(macLights(), label) : label;
        var ring = new Region();
        ring.setMouseTransparent(true);
        var glow = Glow.of(brand.color());
        // Shown while the window is up but the keyboard types into another program.
        var elsewhere = new Label("Click this window before typing: keys are going to another window.");
        elsewhere.setMaxWidth(Double.MAX_VALUE);
        elsewhere.setWrapText(true);
        elsewhere.setMouseTransparent(true);
        elsewhere.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #5c4400; -fx-font-weight: bold; -fx-font-size: 11; -fx-padding: 6 14;"
            + " -fx-border-color: transparent transparent #e0c060 transparent;");
        elsewhere.setVisible(false);
        var content = new StackPane(body, elsewhere);
        StackPane.setAlignment(elsewhere, Pos.TOP_CENTER);
        VBox.setVgrow(content, Priority.ALWAYS);
        Scene scene;
        Region card;
        Node glass;
        Runnable paint;

        if (style == Style.GLOW) {
            var band = new BorderPane();
            band.setLeft(left);
            if (!MAC && closable) band.setRight(winClose());
            glass = glass(band);
            card = new VBox(glass, content);
            card.setStyle("-fx-background-color: white; -fx-background-radius: " + RADIUS + ";");
            var clip = new Rectangle();
            clip.setArcWidth(RADIUS * 2);
            clip.setArcHeight(RADIUS * 2);
            clip.widthProperty().bind(card.widthProperty());
            clip.heightProperty().bind(card.heightProperty());
            card.setClip(clip);
            var lit = new StackPane(card, ring);
            var root = new StackPane(lit);
            root.setPadding(new Insets(HALO));
            root.setStyle("-fx-background-color: transparent;");
            dragWith(glass);
            if (resizable) new EdgeResize(stage, lit).install(root);
            scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            paint = () -> {
                var on = live;
                card.setEffect(on ? null : Glow.grey());
                lit.setEffect(on ? glow.halo() : Glow.IDLE_SHADOW);
                ring.setStyle("-fx-border-radius: " + RADIUS + "; -fx-border-width: 1.5; -fx-border-color: " + (on ? glow.rim() : "#c4c4c4") + ";");
            };
        } else {
            HeaderBar.setSystemButtonHeight(stage, 0);
            var header = new HeaderBar();
            header.setLeftSystemPadding(false);
            header.setRightSystemPadding(false);
            HeaderBar.setDragType(left, HeaderDragType.DRAGGABLE_SUBTREE);
            header.setLeft(left);
            if (!MAC && closable) header.setRight(winClose());
            // The HeaderBar's slots take only their content's width, so the glass is painted
            // behind the whole bar, not on a slot: it then spans the window however wide it gets.
            glass = glass(header);
            card = new VBox(glass, content);
            card.setStyle("-fx-background-color: white;");
            scene = new Scene(new StackPane(card, ring));
            paint = () -> {
                var on = live;
                card.setEffect(on ? null : Glow.grey());
                ring.setStyle("-fx-border-width: 2; -fx-border-color: " + (on ? glow.rim() : "#c4c4c4") + ";");
                ring.setEffect(on ? glow.inner() : null);
            };
        }
        var repaint = (Runnable) () -> {
            if (hwnd == 0 && stage.isShowing()) hwnd = Foreground.handle(stage);
            var now = stage.isFocused() && Foreground.isFront(hwnd);
            elsewhere.setVisible(stage.isShowing() && !now);
            if (now == live && painted) return;
            live = now;
            painted = true;
            paint.run();
        };
        stage.focusedProperty().addListener((o, was, now) -> repaint.run());
        // Windows says nothing to JavaFX when it refuses the foreground, so ask it a few times a second.
        var poll = new Timeline(new KeyFrame(Duration.millis(150), x -> repaint.run()));
        poll.setCycleCount(Animation.INDEFINITE);
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, x -> { hwnd = Foreground.handle(stage); repaint.run(); poll.play(); });
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, x -> poll.stop());
        repaint.run();

        stage.setScene(scene);
        stage.setTitle(brand.product().toUpperCase() + ": " + subtitle);
        stage.getIcons().setAll(brand.icons().window(TaskbarTheme.isDark()));
        // the taskbar's light/dark theme can change while the window is open; the listener lives as long as the stage
        javafx.beans.value.ChangeListener<Boolean> theme = (o, was, dark) -> stage.getIcons().setAll(brand.icons().window(dark));
        stage.getProperties().put(TaskbarTheme.class, theme);
        TaskbarTheme.darkProperty().addListener(new javafx.beans.value.WeakChangeListener<>(theme));
        stage.setOnCloseRequest(e -> { e.consume(); requestClose(); });
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> {
            stage.toFront();
            if (!taskbar) {
                TaskbarButton.remove(stage.getTitle());
                // the shell sometimes adds the button only after the first activation
                var again = new PauseTransition(Duration.millis(300));
                again.setOnFinished(x -> TaskbarButton.remove(stage.getTitle()));
                again.play();
            }
        });
        return scene;
    }

    void requestClose() { if (closable) onClose.run(); }

    StackPane glass(Node content) {
        var sheen = new Region();
        sheen.setMouseTransparent(true);
        sheen.setStyle(Glow.SHEEN);
        var g = new StackPane(sheen, content);
        g.setStyle("-fx-background-color: " + Glow.glass(brand.color()) + ";");
        g.setMaxWidth(Double.MAX_VALUE);
        return g;
    }

    /// The band moves a GLOW window (a NATIVE one is moved by the platform through its HeaderBar).
    void dragWith(Node handle) {
        double[] at = new double[2];
        handle.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> { at[0] = e.getScreenX() - stage.getX(); at[1] = e.getScreenY() - stage.getY(); });
        handle.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> { stage.setX(e.getScreenX() - at[0]); stage.setY(e.getScreenY() - at[1]); });
    }

    /// Windows 11 style: a wide flat ✕ at the top right, red on hover.
    Node winClose() {
        var b = new Button("✕");
        b.setFocusTraversable(false);
        b.setMnemonicParsing(false);
        var style = "-fx-background-color: %s; -fx-background-radius: 0 " + (this.style == Style.GLOW ? RADIUS : 0) + " 0 0;"
            + " -fx-text-fill: white; -fx-font-size: 13; -fx-pref-width: 46; -fx-pref-height: 32; -fx-cursor: default;";
        b.setStyle(style.formatted("transparent"));
        b.setOnMouseEntered(e -> b.setStyle(style.formatted("#c42b1c")));
        b.setOnMouseExited(e -> b.setStyle(style.formatted("transparent")));
        b.setOnAction(e -> requestClose());
        BorderPane.setAlignment(b, Pos.TOP_RIGHT);
        HeaderBar.setAlignment(b, Pos.TOP_RIGHT);
        return b;
    }

    /// macOS traffic lights in their own strip above the band: close live, minimise and zoom shown
    /// disabled, as a mac dialog shows them.
    Node macLights() {
        var close = light("#ff5f57", "#e0443e");
        close.setOnMouseClicked(e -> requestClose());
        var row = new HBox(8, close, light("#d6d6d6", "#c4c4c4"), light("#d6d6d6", "#c4c4c4"));
        row.setPadding(new Insets(8, 12, 0, 12));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    static Circle light(String fill, String stroke) {
        var c = new Circle(6, Color.web(fill));
        c.setStroke(Color.web(stroke));
        c.setStrokeWidth(0.5);
        return c;
    }

    /// A short notice with one OK (Enter or Esc), blocking until it is dismissed.
    public static void tell(Brand brand, String subtitle, String text) {
        var f = new GateFrame(brand, subtitle).modal();
        var ok = Parts.button("OK", "#555", f.stage::close);
        ok.setDefaultButton(true);
        ok.setCancelButton(true);
        f.scene(messageBody(text, ok));
        f.stage.showAndWait();
    }

    /// A yes/no question, blocking until answered. Enter is `yes`, Esc or close is no.
    public static boolean ask(Brand brand, String subtitle, String text, String yes, String no) {
        var f = new GateFrame(brand, subtitle).modal();
        var result = new boolean[] { false };
        var y = Parts.button("Enter  " + yes, Parts.GREEN, () -> { result[0] = true; f.stage.close(); });
        y.setDefaultButton(true);
        var n = Parts.button("Esc  " + no, "#555", f.stage::close);
        n.setCancelButton(true);
        f.scene(messageBody(text, y, n));
        f.stage.showAndWait();
        return result[0];
    }

    static VBox messageBody(String text, Button... buttons) {
        var msg = new Label(text);
        msg.setWrapText(true);
        var box = new VBox(14, msg, new HBox(8, buttons));
        box.setPadding(new Insets(16));
        box.setPrefWidth(420);
        return box;
    }
}
