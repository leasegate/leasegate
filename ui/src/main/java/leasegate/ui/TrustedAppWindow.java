package leasegate.ui;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import leasegate.frame.*;
import leasegate.core.access.*;
import leasegate.core.trusted.*;
import leasegate.core.trusted.TrustedAppApproval.*;

/// The trusted-app approval window: simpler than the process-tree one, because the approval belongs
/// to one run of one exact program. The app's own icon and name, how far its code was verified, what
/// it asks for in one line, how long, and the passphrase. Keys are ignored while it settles; F3/F4
/// pick the duration, F2 approves once the passphrase checks out, Esc denies.
final class TrustedAppWindow<S extends Scope<S>> {

    final Request<S> r;
    final PassphraseGate gate;
    final LeaseRules<S> rules;
    final Brand brand;
    final Consumer<Optional<Decision>> onDecision;
    final DurationChipsView chips = new DurationChipsView(DurationChips.TRUSTED_APP);
    final SecretField passphrase = new SecretField();
    final Label until = ApprovalParts.line(" ", ApprovalParts.GREY), error = PassphrasePrompt.errorLabel();
    final GateFrame frame;
    final Stage stage;
    Arming arming;
    javafx.scene.control.Button approve;
    boolean decided;

    TrustedAppWindow(Request<S> r, PassphraseGate gate, LeaseRules<S> rules, Brand brand, Consumer<Optional<Decision>> onDecision) {
        this.r = r;
        this.gate = gate;
        this.rules = rules;
        this.brand = brand;
        this.onDecision = onDecision;
        this.frame = new GateFrame(brand, "trusted app: " + r.declared().name() + " asks for a long approval").onClose(() -> decide(Optional.empty()));
        this.stage = frame.stage;
    }

    void show() {
        approve = ApprovalParts.button("F2  Approve", ApprovalParts.GREEN, this::approve);
        var deny = ApprovalParts.button("Esc  Deny", ApprovalParts.RED, () -> decide(Optional.empty()));
        var armingLine = ApprovalParts.line(" ", ApprovalParts.AMBER);
        arming = new Arming(List.of(approve, deny), armingLine);
        chips.setCap(r.capMinutes(), r.requestedMinutes() == null ? DurationChips.TRUSTED_APP_DEFAULT : r.requestedMinutes(), "the most this app may be granted");
        showUntil();

        var asks = rules.summary(r.scope());
        var root = new VBox(10, TrustedParts.header(r.declared().name(), r.code().icon(), TrustedParts.level(r.check())));
        root.getChildren().addAll(TrustedAppLines.of(r));
        root.getChildren().addAll(ApprovalParts.line("asks for: " + asks.text(), ApprovalParts.color(asks.tone())),
            chips.node(), until, new Label("Passphrase"), passphrase, error, new HBox(8, approve, deny), armingLine,
            ApprovalParts.small("F3/F4 duration · F2 approve · Esc deny", "#777"));
        root.setPadding(new Insets(14));
        root.setPrefWidth(620);

        var scene = frame.scene(root);
        arming.guard(scene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        stage.setTitle(brand.product().toUpperCase() + ": trusted app " + r.declared().name());
        stage.show();
        stage.toFront();
        passphrase.requestFocus();
    }

    void onKey(KeyEvent e) {
        if (!arming.armed) { e.consume(); return; }
        switch (e.getCode()) {
            case F3, F4 -> { chips.move(e.getCode() == javafx.scene.input.KeyCode.F4); showUntil(); }
            case F2 -> approve();
            case ESCAPE -> decide(Optional.empty());
            default -> { return; }
        }
        e.consume();
    }

    void showUntil() {
        var m = chips.selectedMinutes();
        until.setText(m <= 0 ? " " : "until " + TrustedParts.when(Instant.now().plusSeconds(m * 60L))
            + ", for this run of the app only. It keeps working while " + brand.product() + " is locked"
            + (r.hostLocked() ? " (it is locked now)" : "") + ", until that time.");
    }

    void approve() {
        if (!arming.armed || decided) return;
        var why = chips.whyNone();
        if (why == null && passphrase.getText().isEmpty()) why = "Type the passphrase first.";
        if (why != null) { error.setText(why); return; }
        var minutes = chips.selectedMinutes();
        var typed = passphrase.getText();
        PassphrasePrompt.check(gate, typed, approve, error, () -> decide(Optional.of(new Decision(minutes, typed))), passphrase::clear);
    }

    /// The requester is gone: close as a denial nobody will read.
    void withdraw() { decide(Optional.empty()); }

    void decide(Optional<Decision> d) {
        if (decided) return;
        decided = true;
        passphrase.clear();
        onDecision.accept(d);
        stage.close();
    }
}
