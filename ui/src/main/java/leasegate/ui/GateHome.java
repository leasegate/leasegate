package leasegate.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import leasegate.frame.*;
import leasegate.core.access.Scope;

/// The gate's own window, opened from the tray icon: the gate's big mark (so two gates are told
/// apart at a glance), its state, who has access right now, and everything the tray menu offers as
/// F-keys. The legend is the toolbar: each key is a button wearing its key, and both run the same
/// MenuItem. No text input here, but actions still go on F-keys, as in every gate window.
/// F2 (approve elsewhere) and F9 (show secrets) are left unbound on purpose.
public final class GateHome<S extends Scope<S>> {

    static final KeyCode[] GATE_KEYS = { KeyCode.F3, KeyCode.F4, KeyCode.F5, KeyCode.F6, KeyCode.F7 };

    final TrayShell<S> tray;
    final AccessRows<S> rows;
    Stage stage;
    Label status, update;
    FlowPane legend;
    List<Action> actions = List.of();
    String shown = "";
    Timeline ticker;

    GateHome(TrayShell<S> tray) {
        this.tray = tray;
        this.rows = new AccessRows<>(tray.engine, tray.passphraseGate, tray.brand);
    }

    public void show() {
        if (stage != null && stage.isShowing()) { stage.toFront(); stage.requestFocus(); return; }
        var brand = tray.brand;
        var f = GateFrame.resizable(brand, "state and access").notOnTop();
        stage = f.stage;
        status = new Label();
        status.setWrapText(true);
        status.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #222;");
        var hero = new VBox(10, brand.logo(104, tray.tagline), status);
        var scroll = new ScrollPane(rows.node);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(210);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: white;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        legend = new FlowPane(8, 8);
        update = Parts.small("", "#555");
        var footer = new HBox(10, Parts.small(brand.product() + " " + UpdateCheck.version() + "  ·  Esc closes", "#777"), AccessRows.spacer(), update);
        footer.setAlignment(Pos.CENTER_LEFT);
        var body = new VBox(12, hero, heading("WHO HAS ACCESS NOW"), scroll, heading("ACTIONS"), legend, footer);
        body.setPadding(new Insets(18));
        body.setPrefWidth(720);
        var scene = f.scene(body);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) { stage.hide(); return; }
            actions.stream().filter(a -> a.key() == e.getCode()).findFirst().ifPresent(a -> { e.consume(); run(a); });
        });
        ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> refresh()));
        ticker.setCycleCount(Animation.INDEFINITE);
        stage.setOnShown(e -> ticker.play());
        stage.setOnHidden(e -> ticker.stop());
        refresh();
        stage.setTitle(brand.product().toUpperCase());
        stage.show();
    }

    public void checkForUpdates() {
        show();
        UpdateCheck.run(tray.brand, line -> { if (update != null) update.setText(line); });
    }

    void refresh() {
        tray.refresh();
        status.setText(tray.statusLine.get());
        rows.refresh();
        actions = actions();
        var signature = String.join("|", actions.stream().map(a -> a.item().getText() + a.item().isDisable()).toList());
        if (signature.equals(shown)) return;
        shown = signature;
        legend.getChildren().setAll(actions.stream().map(this::button).toList());
    }

    /// The gate's own items on F3..F7, then the shared ones.
    List<Action> actions() {
        var list = new ArrayList<Action>();
        var i = 0;
        for (var m : tray.extraItems) if (i < GATE_KEYS.length) list.add(new Action(GATE_KEYS[i++], m));
        list.add(new Action(KeyCode.F8, tray.blanketItem));
        list.add(new Action(KeyCode.F10, tray.revokeItem));
        list.add(new Action(KeyCode.F11, TrayShell.item("Check for updates", this::checkForUpdates)));
        list.add(new Action(KeyCode.F1, TrayShell.item("About", tray::about)));
        return list;
    }

    Button button(Action a) {
        var b = Parts.button(a.key().getName() + "  " + a.item().getText(), a.key() == KeyCode.F10 ? Parts.RED : tray.brand.color(), () -> run(a));
        b.setDisable(a.item().isDisable());
        b.setFocusTraversable(false);
        return b;
    }

    void run(Action a) { if (!a.item().isDisable()) a.item().fire(); refresh(); }

    static Label heading(String text) {
        var l = new Label(text);
        l.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #888;");
        return l;
    }

    record Action(KeyCode key, MenuItem item) {}
}
