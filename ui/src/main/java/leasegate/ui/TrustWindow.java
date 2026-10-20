package leasegate.ui;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import leasegate.frame.*;
import leasegate.core.ProcInfo;
import leasegate.core.access.*;

/// One process, and everything it starts, trusted for hours. The whole window takes the colour of
/// the risk -- green, amber or red -- and changes it as the duration is changed, so how much is
/// being handed over is seen before anything is read. Approving needs the passphrase, typed into
/// the box: Enter approves, Esc denies, ←/→ change the duration.
final class TrustWindow<S extends Scope<S>> {

    final TrustApproval.Request<S> r;
    final PassphraseGate gate;
    final LeaseRules<S> rules;
    final Brand brand;
    final Consumer<Optional<TrustApproval.Decision<S>>> onDecision;
    final DurationChipsView chips = new DurationChipsView(DurationChips.TRUST);
    final GateFrame frame;
    final Stage stage;
    final VBox root = new VBox(10);
    final Label band = new Label();
    final SecretField passphrase = new SecretField();
    final Label error = PassphrasePrompt.errorLabel();
    boolean decided;

    TrustWindow(TrustApproval.Request<S> r, PassphraseGate gate, LeaseRules<S> rules, Brand brand, Consumer<Optional<TrustApproval.Decision<S>>> onDecision) {
        this.r = r;
        this.gate = gate;
        this.rules = rules;
        this.brand = brand;
        this.onDecision = onDecision;
        this.frame = new GateFrame(brand, "trust request: a process and everything it starts").onClose(() -> decide(Optional.empty()));
        this.stage = frame.stage;
    }

    void show() {
        chips.setCap(TrustApproval.MAX_MINUTES, r.requested().requestedMinutes() == null ? 480 : r.requested().requestedMinutes(),
            "the passphrase");
        band.setMaxWidth(Double.MAX_VALUE);
        var t = r.target();
        var name = new Label(t.imageName() + "  (PID " + t.pid() + ")");
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        var what = ApprovalParts.small("Trust this process and every program it starts, whatever their executable, until the time below."
            + " The trust ends early if this process exits.", "#333");
        var asked = ApprovalParts.small("asked by \"" + r.app() + "\" · PID " + r.client().pid() + " · " + r.client().imagePath(), ApprovalParts.GREY);
        var approve = ApprovalParts.button("Enter  Approve", "#2e7d32", this::approve);
        var deny = ApprovalParts.button("Esc  Deny", "#b3261e", () -> decide(Optional.empty()));
        passphrase.setPromptText("passphrase");
        root.getChildren().addAll(band, name, what,
            ApprovalParts.box("command line of the process to be trusted", ProcInfo.commandLine(t.pid())),
            ApprovalParts.box("its working directory", ProcInfo.currentDirectory(t.pid())),
            asked, ApprovalParts.scope(r.requested(), rules), chips.node(),
            new VBox(4, ApprovalParts.small("PASSPHRASE", "#777"), passphrase, error),
            new HBox(8, approve, deny), ApprovalParts.small("←/→ duration · Enter approve · Esc deny", "#777"));
        root.setPadding(new Insets(14));
        root.setPrefWidth(660);
        paint();

        var scene = frame.scene(root);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        stage.setTitle(brand.product().toUpperCase() + ": trust " + t.imageName() + " for hours");
        stage.show();
        stage.toFront();
        passphrase.requestFocus();
    }

    void paint() {
        var minutes = chips.selectedMinutes();
        var risk = TrustRisk.of(rules, r.requested(), minutes);
        band.setText(risk.badge + " · " + DurationChips.label(minutes) + " · until "
            + GrantExtend.when(Instant.now().plusSeconds(minutes * 60L)));
        band.setStyle("-fx-background-color: " + risk.accent + "; -fx-text-fill: white; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 8 14;");
        root.setStyle("-fx-background-color: " + risk.tint + ";");
    }

    void onKey(KeyEvent e) {
        switch (e.getCode()) {
            case LEFT, RIGHT -> { chips.move(e.getCode() == KeyCode.RIGHT); paint(); e.consume(); }
            case ENTER -> { approve(); e.consume(); }
            case ESCAPE -> { decide(Optional.empty()); e.consume(); }
            default -> {}
        }
    }

    void approve() {
        if (!gate.verify(passphrase.getText())) { error.setText("wrong passphrase"); passphrase.clear(); return; }
        decide(Optional.of(new TrustApproval.Decision<>(chips.selectedMinutes(), r.requested())));
    }

    void withdraw() {
        if (decided) return;
        decided = true;
        stage.close();
    }

    void decide(Optional<TrustApproval.Decision<S>> d) {
        if (decided) return;
        decided = true;
        stage.close();
        onDecision.accept(d);
    }
}
