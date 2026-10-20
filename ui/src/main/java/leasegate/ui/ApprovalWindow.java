package leasegate.ui;

import java.util.Optional;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import leasegate.core.access.*;

/// The one approval window, for both kinds of request. Attested: the human picks how far up the
/// process tree the approval reaches, and a broader anchor visibly shortens the longest duration.
/// Keyed: the approval belongs to the app's key and its length is capped by how narrow the scope
/// is. Nothing is granted until Approve. No text input here, so bare letters are the shortcuts.
final class ApprovalWindow<S extends Scope<S>> {

    final Approval.Request<S> r;
    final PassphraseGate passphraseGate;
    final LeaseRules<S> rules;
    final Consumer<Optional<Approval.Decision<S>>> onDecision;
    final boolean keyed;
    final AncestryTableView table;
    final DurationChipsView chips;
    final Stage stage = new Stage(StageStyle.UTILITY);
    boolean decided;

    ApprovalWindow(Approval.Request<S> r, PassphraseGate passphraseGate, LeaseRules<S> rules, Consumer<Optional<Approval.Decision<S>>> onDecision) {
        this.r = r;
        this.passphraseGate = passphraseGate;
        this.rules = rules;
        this.onDecision = onDecision;
        this.keyed = r.kind() == Approval.Kind.KEYED;
        this.table = keyed ? null : new AncestryTableView(AncestryRow.build(r.chain(), r.tree(), r.requested(), rules));
        this.chips = new DurationChipsView(keyed ? DurationChips.KEYED : DurationChips.ATTESTED);
    }

    void show() {
        var approve = ApprovalParts.button("A  Approve", "#2e7d32", this::approve);
        var deny = ApprovalParts.button("D  Deny", "#b3261e", () -> decide(Optional.empty()));
        var legend = ApprovalParts.small((keyed ? "" : "↑/↓ anchor · ") + "←/→ duration · A approve · D or Esc deny", "#777");

        var root = new VBox(10, ApprovalParts.header(r), ApprovalParts.scope(r.requested(), rules));
        if (keyed) {
            root.getChildren().add(ApprovalParts.keyedNote(r));
            chips.setCap(rules.keyedMaxMinutes(r.requested()), r.requested().requestedMinutes(), "how narrowly it is scoped");
        } else {
            root.getChildren().add(ApprovalParts.small("Choose how far up the process tree this approval reaches."
                + " Broader anchors allow shorter durations. The approval ends when the anchor exits.", "#333"));
            var scroll = new ScrollPane(table.node());
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(Math.min(240, 34 + table.rows.size() * 24));
            root.getChildren().add(scroll);
            table.onSelectionChange(this::onAnchorChanged);
            var hops = table.selectableHopsAscending();
            var suggested = AnchorSuggestion.suggest(r.chain(), r.tree());
            table.selectHop(suggested >= 0 ? suggested : hops.isEmpty() ? -1 : hops.getLast());
            onAnchorChanged();
        }
        root.getChildren().addAll(chips.node(), new HBox(8, approve, deny), legend);
        root.setPadding(new Insets(14));
        root.setPrefWidth(660);

        var scene = new Scene(root);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        stage.setTitle(rules.product() + ": request from " + r.app());
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.setOnCloseRequest(e -> decide(Optional.empty()));
        stage.show();
        stage.toFront();
    }

    void onAnchorChanged() {
        var row = table.selectedHop().map(table.rows::get);
        chips.setCap(row.map(x -> x.cap().minutes()).orElse(0), r.requested().requestedMinutes(),
            row.map(x -> x.cap().boundBy()).orElse(""));
    }

    void onKey(KeyEvent e) {
        switch (e.getCode()) {
            case UP, DOWN -> { if (!keyed) moveAnchor(e.getCode() == KeyCode.DOWN); }
            case LEFT, RIGHT -> chips.move(e.getCode() == KeyCode.RIGHT);
            case A -> approve();
            case D, ESCAPE -> decide(Optional.empty());
            default -> { return; }
        }
        e.consume();
    }

    void moveAnchor(boolean down) {
        var hops = table.selectableHopsAscending();
        if (hops.isEmpty()) return;
        var idx = hops.indexOf(table.selectedHop().orElse(hops.getFirst()));
        table.selectHop(hops.get(down ? Math.min(idx + 1, hops.size() - 1) : Math.max(idx - 1, 0)));
    }

    void approve() {
        var minutes = chips.selectedMinutes();
        if (minutes <= 0) return;
        AncestryNode anchor = null;
        var cap = rules.keyedMaxMinutes(r.requested());
        if (!keyed) {
            var row = table.selectedHop().map(table.rows::get).filter(AncestryRow::selectable).orElse(null);
            if (row == null || minutes > row.cap().minutes()) return;
            anchor = row.node();
            cap = row.cap().minutes();
        }
        if (rules.needsPassphrase(r.requested()) && !PassphrasePrompt.confirmOnce(passphraseGate,
                rules.product() + ": confirm a wide approval", "This request reaches everything this gate guards."
                    + " Type the passphrase to approve it anyway.")) return;
        decide(Optional.of(new Approval.Decision<>(anchor, minutes, cap, r.requested())));
    }

    /// The requester is gone: close without asking, as a denial nobody will read.
    void withdraw() { decide(Optional.empty()); }

    void decide(Optional<Approval.Decision<S>> d) {
        if (decided) return;
        decided = true;
        onDecision.accept(d);
        stage.close();
    }
}
