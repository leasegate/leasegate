package leasegate.ui;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import leasegate.frame.*;
import leasegate.core.LeaseEngine;
import leasegate.core.access.*;

/// Who can get through right now, in a window of its own (see AccessRows). Ticks every second while
/// shown; a row goes the moment its approval ends, and revoking cuts the live connections too.
public final class AccessListWindow<S extends Scope<S>> {

    final LeaseEngine<S> engine;
    final Brand brand;
    final AccessRows<S> rows;
    Stage stage;
    Timeline ticker;

    public AccessListWindow(LeaseEngine<S> engine, PassphraseGate passphraseGate, Brand brand) {
        this.engine = engine;
        this.brand = brand;
        this.rows = new AccessRows<>(engine, passphraseGate, brand);
    }

    String product() { return engine.rules.product(); }

    public void show() {
        if (stage != null && stage.isShowing()) { stage.toFront(); return; }
        var frame = GateFrame.resizable(brand, "who has access right now").notOnTop();
        stage = frame.stage;
        var revokeAll = new Hyperlink("revoke every attested approval now");
        revokeAll.setMnemonicParsing(false);
        revokeAll.setOnAction(e -> { engine.revokeAllNow(); rows.refresh(); });
        var header = new HBox(AccessRows.spacer(), revokeAll);
        header.setAlignment(Pos.CENTER_LEFT);
        var scroll = new ScrollPane(rows.node);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(320);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        var root = new VBox(10, header, scroll, ApprovalParts.small("Esc closes · keyed approvals survive a restart, attested ones do not", "#777"));
        root.setPadding(new Insets(12));
        root.setPrefWidth(720);
        ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> rows.refresh()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
        stage.setOnHidden(e -> ticker.stop());
        rows.refresh();
        var scene = frame.scene(root);
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) stage.hide(); });
        stage.setTitle(product().toUpperCase() + ": access");
        stage.show();
    }

    static String imageName(String path) { return AccessRows.imageName(path); }
}
