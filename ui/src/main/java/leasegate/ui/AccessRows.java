package leasegate.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import leasegate.frame.*;
import leasegate.core.LeaseEngine;
import leasegate.core.access.*;

/// One row per attested grant and keyed approval: who, what it may reach, time left, live
/// connections, and its revoke/extend links. Shown by the access list and by the gate's home window.
public final class AccessRows<S extends Scope<S>> {

    final LeaseEngine<S> engine;
    final PassphraseGate passphraseGate;
    final Brand brand;
    public final VBox node = new VBox(6);

    public AccessRows(LeaseEngine<S> engine, PassphraseGate passphraseGate, Brand brand) {
        this.engine = engine;
        this.passphraseGate = passphraseGate;
        this.brand = brand;
    }

    public int size() { return engine.activeGrants().size() + (int) engine.keyedApps().stream().filter(a -> !a.expired(Instant.now())).count(); }

    public void refresh() {
        var list = new ArrayList<Node>();
        engine.activeGrants().forEach(g -> list.add(grantRow(g)));
        var now = Instant.now();
        engine.keyedApps().stream().filter(a -> !a.expired(now)).forEach(a -> list.add(keyedRow(a)));
        node.getChildren().setAll(list.isEmpty() ? List.of(ApprovalParts.small("Nothing has access right now. A program that asks gets an approval window first.", "#777")) : list);
    }

    Node grantRow(Grant<S> g) {
        var revoke = new Hyperlink("revoke");
        revoke.setOnAction(e -> { engine.revokeGrant(g.id()); refresh(); });
        var extend = new Hyperlink("extend");
        extend.setOnAction(e -> { GrantExtend.run(engine, passphraseGate, brand, g); refresh(); });
        return row(g.anyClient() ? "trusted" : "attested", g.anyClient() ? "any program" : imageName(g.clientImagePath()), (g.anyClient() ? "under " : "anchored at ") + imageName(g.anchorImagePath()) + " (" + g.anchorPid() + ")",
            g.scope(), g.expiresAt(), engine.liveConnections(g.id()), false, extend, revoke);
    }

    Node keyedRow(KeyedApp<S> a) {
        var revoke = new Hyperlink("revoke");
        revoke.setOnAction(e -> { engine.revokeKeyed(a.id()); refresh(); });
        var dismiss = new Hyperlink("dismiss flag");
        dismiss.setOnAction(e -> { engine.clearKeyedFlag(a.id()); refresh(); });
        var links = a.flagged() ? new Hyperlink[]{dismiss, revoke} : new Hyperlink[]{revoke};
        return row("keyed", a.app(), "key " + a.fingerprint() + (a.lastSeenImagePath() == null ? "" : ", last from " + imageName(a.lastSeenImagePath())),
            a.scope(), a.expiresAt(), engine.liveConnections(a.id()), a.flagged(), links);
    }

    Node row(String kind, String who, String how, S scope, Instant expires, long live, boolean flagged, Hyperlink... actions) {
        var title = new Label(who + "  ·  " + kind + (flagged ? "  ·  EXECUTABLE CHANGED" : ""));
        title.setStyle("-fx-font-weight: bold;" + (flagged ? " -fx-text-fill: #b3261e;" : ""));
        var remaining = new Label(remaining(expires));
        remaining.setStyle("-fx-font-family: Consolas, monospace; -fx-font-weight: bold;");
        var top = new HBox(10, title, spacer(), remaining, new Label(live + " live"));
        for (var a : actions) { a.setMnemonicParsing(false); a.setFocusTraversable(false); }
        top.getChildren().addAll(actions);
        top.setAlignment(Pos.CENTER_LEFT);
        var summary = engine.rules.summary(scope);
        var box = new VBox(2, top, ApprovalParts.small(how, "#555"), ApprovalParts.small(summary.text(), ApprovalParts.color(summary.tone())));
        box.setStyle("-fx-border-color: #ddd; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 6 0;");
        return box;
    }

    static String remaining(Instant expires) {
        var s = Math.max(0, Duration.between(Instant.now(), expires).toSeconds());
        return s >= 86400 ? "%dd %02dh".formatted(s / 86400, (s % 86400) / 3600) : "%02d:%02d:%02d".formatted(s / 3600, (s % 3600) / 60, s % 60);
    }

    static String imageName(String path) { var i = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/')); return (i < 0 ? path : path.substring(i + 1)).toLowerCase(); }

    static Node spacer() { var r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }
}
