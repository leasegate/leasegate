package leasegate.tray;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import static leasegate.tray.TrayParts.*;

/// A tray menu drawn by JavaFX instead of the platform's AWT menu (which cannot draw "…" or "·",
/// and knows nothing of the app's colour). The pattern Swing tray apps use: the tray icon carries no
/// menu, a click opens a borderless window at the cursor, and losing the focus closes it.
/// The items are plain MenuItems (text, disabled, onAction, SeparatorMenuItem), asked for afresh
/// each time it opens. Arrows move, Enter runs, Esc closes. Use with FxTray, or open it yourself.
public final class TrayMenu {

    static final double PAD = 14;

    final Image icon;
    final String title, color;
    final Supplier<String> status;
    final Supplier<List<MenuItem>> items;
    Stage owner, stage;
    StackPane root;
    VBox card;
    Label statusLine;
    final List<Row> rows = new ArrayList<>();
    String shown;
    int selected = -1;
    long closedAt;

    /// `color` is a CSS colour (#2e6da8); `status` is the line under the title, null for none.
    public TrayMenu(Image icon, String title, String color, Supplier<String> status, Supplier<List<MenuItem>> items) {
        this.icon = icon;
        this.title = title;
        this.color = color;
        this.status = status;
        this.items = items;
    }

    public boolean isShowing() { return stage != null && stage.isShowing(); }

    /// Opens at the cursor (screen coordinates), or closes it when open, as a platform menu does.
    public void toggle(double x, double y) {
        if (isShowing()) { close(); return; }
        if (System.currentTimeMillis() - closedAt < 250) return;   // the click that took the focus away
        show(x, y);
    }

    /// One window, built once and shown again each time: building a window costs ~60-130 ms a time,
    /// and the first one ~1.4 s (classes, CSS, the native window). Rows are rebuilt only when an
    /// item's text or state changed.
    public void show(double x, double y) {
        build();
        statusLine.setText(status == null ? "" : status.get());
        statusLine.setManaged(!statusLine.getText().isBlank());
        var list = items.get();
        var signature = String.join("|", list.stream().map(m -> m instanceof SeparatorMenuItem ? "-" : m.getText() + m.isDisable()).toList());
        if (!signature.equals(shown)) { shown = signature; rows(list); }
        else rows.replaceAll(r -> new Row(list.get(r.index()), r.box(), r.index()));
        if (selected >= 0) rows.get(selected).box().setStyle("");
        selected = -1;
        root.applyCss();
        root.layout();
        stage.sizeToScene();
        place(x, y, root.prefWidth(-1), root.prefHeight(-1));
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    /// Builds the window off-screen and shows it once, invisibly, so the first real open is fast.
    /// FxTray calls this when it installs the icon.
    public void prewarm() {
        build();
        rows(items.get());
        shown = null;
        stage.setOpacity(0);
        stage.setX(-32000);
        stage.setY(-32000);
        stage.show();
        Platform.runLater(() -> { stage.hide(); stage.setOpacity(1); });
    }

    public void close() {
        if (!isShowing()) return;
        closedAt = System.currentTimeMillis();
        stage.hide();
    }

    void build() {
        if (stage != null) return;
        owner = hiddenOwner();
        stage = new Stage(StageStyle.TRANSPARENT);
        stage.initOwner(owner);
        stage.setAlwaysOnTop(true);
        card = new VBox(header());
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #c8c8c8;");
        card.setEffect(new DropShadow(14, Color.rgb(0, 0, 0, 0.30)));
        card.setMinWidth(300);
        card.setPadding(new Insets(0, 0, 4, 0));
        root = new StackPane(card);
        root.setPadding(new Insets(PAD));
        root.setStyle("-fx-background-color: transparent;");
        var scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ESCAPE -> close();
                case DOWN -> move(1);
                case UP -> move(-1);
                case F2 -> { if (selected >= 0) run(rows.get(selected)); }
                default -> {}
            }
        });
        stage.setScene(scene);
        stage.focusedProperty().addListener((o, was, now) -> { if (was && !now) close(); });
        // Windows may refuse the focus; then leaving the menu with the mouse closes it instead.
        var leave = new PauseTransition(Duration.seconds(1.2));
        leave.setOnFinished(e -> { if (!stage.isFocused()) close(); });
        root.setOnMouseExited(e -> leave.playFromStart());
        root.setOnMouseEntered(e -> leave.stop());
    }

    void rows(List<MenuItem> list) {
        rows.clear();
        card.getChildren().setAll(card.getChildren().getFirst());
        for (var i = 0; i < list.size(); i++) {
            var m = list.get(i);
            card.getChildren().add(m instanceof SeparatorMenuItem ? separator() : row(m, i));
        }
        card.getChildren().add(legend());
    }

    /// Enter and Space run nothing: the menu opens on a click, but a key typed meant for another window must not fire a row.
    Label legend() {
        var l = new Label("↑/↓ choose · F2 run · Esc close");
        l.setStyle("-fx-font-size: 10; -fx-text-fill: #888;");
        l.setPadding(new Insets(4, 16, 2, 16));
        return l;
    }

    /// Bottom right corner at the cursor (the taskbar is usually below), kept inside the screen.
    void place(double x, double y, double w, double h) {
        var screens = Screen.getScreensForRectangle(x, y, 1, 1);
        var b = (screens.isEmpty() ? Screen.getPrimary() : screens.getFirst()).getVisualBounds();
        var top = y - h + PAD;
        if (top < b.getMinY()) top = y - PAD;
        stage.setX(clamp(x - w + PAD, b.getMinX(), b.getMaxX() - w));
        stage.setY(clamp(top, b.getMinY(), b.getMaxY() - h));
    }


    VBox header() {
        var view = new ImageView(icon);
        view.setFitWidth(30);
        view.setFitHeight(30);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        var plate = new StackPane(view);
        plate.setPadding(new Insets(2));
        plate.setStyle("-fx-background-color: white; -fx-background-radius: 7;");
        var name = new Label(title.toUpperCase());
        name.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: white;");
        statusLine = new Label();
        statusLine.setStyle("-fx-font-size: 11; -fx-text-fill: white;");
        statusLine.setWrapText(true);
        statusLine.setMaxWidth(360);
        statusLine.visibleProperty().bind(statusLine.managedProperty());
        var text = new VBox(1, name, statusLine);
        var row = new HBox(10, plate, text);
        row.setAlignment(Pos.CENTER_LEFT);
        var band = new VBox(row);
        band.setPadding(new Insets(9, 14, 9, 12));
        band.setStyle("-fx-background-color: " + glass(color) + "; -fx-background-radius: 10 10 0 0;");
        VBox.setMargin(band, new Insets(0, 0, 4, 0));
        return band;
    }

    /// `at` is the item's place in the list, so a reused row runs the item of the latest list.
    HBox row(MenuItem m, int at) {
        var label = new Label(m.getText());
        label.setMnemonicParsing(false);
        label.setStyle("-fx-font-size: 13; -fx-text-fill: " + (m.isDisable() ? "#a0a0a0" : "#1f1f1f") + ";");
        var box = new HBox(label);
        box.setPadding(new Insets(6, 16, 6, 16));
        box.setMaxWidth(Double.MAX_VALUE);
        if (!m.isDisable()) {
            var index = rows.size();
            rows.add(new Row(m, box, at));
            box.setOnMouseEntered(e -> select(index));
            box.setOnMouseClicked(e -> run(rows.get(index)));
        }
        return box;
    }

    void move(int step) {
        if (rows.isEmpty()) return;
        select(selected < 0 ? (step > 0 ? 0 : rows.size() - 1) : Math.floorMod(selected + step, rows.size()));
    }

    void select(int index) {
        if (selected >= 0) rows.get(selected).box().setStyle("");
        selected = index;
        rows.get(index).box().setStyle("-fx-background-color: " + rgba(Color.web(color), 0.16) + ";");
    }

    void run(Row r) {
        close();
        Platform.runLater(r.item()::fire);
    }


    record Row(MenuItem item, HBox box, int index) {}
}
