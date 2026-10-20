package leasegate.frame;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/// A passphrase field with an eye beside it, for passphrases typed rarely and mistyped easily.
/// Press and hold the eye -- or hold F9 -- to see what was typed; let go and it is hidden again.
/// Hold it for over a second and it stays shown (the eye turns amber); press again to hide.
/// F9 shows every secret field of the window at once, so a passphrase and its confirmation can be
/// compared. F9 is used for nothing else in any window, so it is safe while typing.
public final class SecretField extends HBox {

    public static final KeyCode KEY = KeyCode.F9;
    static final Duration HOLD_TO_KEEP = Duration.millis(1100);
    static final String SCENE_KEY = "leasegate.frame.SecretField";

    final PasswordField hidden = new PasswordField();
    final TextField shown = new TextField();
    final Label eye = new Label();
    final PauseTransition holdTimer = new PauseTransition(HOLD_TO_KEEP);
    boolean revealed, kept, releaseHides;

    public SecretField() {
        shown.textProperty().bindBidirectional(hidden.textProperty());
        shown.promptTextProperty().bind(hidden.promptTextProperty());
        shown.setVisible(false);
        var both = new StackPane(hidden, shown);
        HBox.setHgrow(both, Priority.ALWAYS);

        eye.setFocusTraversable(false);
        eye.setPadding(new Insets(2, 8, 2, 6));
        eye.setTooltip(new Tooltip("Hold to see the passphrase; hold over a second to keep it shown. Same with F9."));
        eye.setOnMousePressed(e -> press());
        eye.setOnMouseReleased(e -> release());
        holdTimer.setOnFinished(e -> { kept = true; paint(); });

        setSpacing(4);
        setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(both, eye);
        paint();
        sceneProperty().addListener((o, was, now) -> { if (now != null) installKey(now); });
    }

    public String getText() { return hidden.getText(); }

    public void setText(String text) { hidden.setText(text); }

    public StringProperty textProperty() { return hidden.textProperty(); }

    public void setPromptText(String text) { hidden.setPromptText(text); }

    public void clear() { hidden.clear(); }

    /// The field the human sees now: Enter, focus and caret follow it.
    public TextField input() { return revealed ? shown : hidden; }

    @Override public void requestFocus() { input().requestFocus(); }

    void press() {
        if (kept) { kept = false; releaseHides = false; show(false); return; }
        releaseHides = true;
        show(true);
        holdTimer.playFromStart();
    }

    void release() {
        holdTimer.stop();
        if (releaseHides && !kept) show(false);
        releaseHides = false;
    }

    void show(boolean on) {
        var from = input();
        var focused = from.isFocused();
        var caret = from.getCaretPosition();
        revealed = on;
        shown.setVisible(on);
        hidden.setVisible(!on);
        var to = input();
        if (focused) { to.requestFocus(); to.positionCaret(caret); }
        paint();
    }

    void paint() {
        eye.setGraphic(icon(revealed));
        eye.setText("F9");
        eye.setStyle("-fx-font-size: 10; -fx-text-fill: #555; -fx-background-radius: 4; -fx-cursor: hand; -fx-background-color: "
            + (kept ? "#ffe08a" : revealed ? "#e8e8e8" : "transparent") + ";");
    }

    /// An eye, struck through while the passphrase is hidden.
    static Group icon(boolean open) {
        var outline = new SVGPath();
        outline.setContent("M1 8 C 4 2.5, 14 2.5, 17 8 C 14 13.5, 4 13.5, 1 8 Z");
        outline.setFill(Color.TRANSPARENT);
        outline.setStroke(Color.web("#444"));
        outline.setStrokeWidth(1.4);
        var pupil = new Circle(9, 8, 2.6, Color.web("#444"));
        var g = new Group(outline, pupil);
        if (!open) {
            var slash = new Line(3, 14, 15, 2);
            slash.setStroke(Color.web("#444"));
            slash.setStrokeWidth(1.6);
            g.getChildren().add(slash);
        }
        return g;
    }

    /// One F9 handler per window, driving every secret field in it. A held key repeats its
    /// KEY_PRESSED, so only the first press of a hold counts.
    static void installKey(Scene scene) {
        if (scene.getProperties().containsKey(SCENE_KEY)) return;
        var down = new boolean[1];
        scene.getProperties().put(SCENE_KEY, Boolean.TRUE);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KEY) return;
            if (!down[0]) { down[0] = true; fields(scene).forEach(SecretField::press); }
            e.consume();
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            if (e.getCode() != KEY) return;
            down[0] = false;
            fields(scene).forEach(SecretField::release);
            e.consume();
        });
    }

    static List<SecretField> fields(Scene scene) {
        var out = new ArrayList<SecretField>();
        collect(scene.getRoot(), out);
        return out;
    }

    static void collect(javafx.scene.Node n, List<SecretField> out) {
        if (n instanceof SecretField f) { out.add(f); return; }
        if (n instanceof javafx.scene.Parent p) for (var c : p.getChildrenUnmodifiable()) collect(c, out);
    }
}
