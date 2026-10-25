package leasegate.ui;

import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import leasegate.frame.*;
import leasegate.core.access.PassphraseGate;
import leasegate.core.trusted.*;

/// The gate's passphrase prompt, shown on behalf of a trusted app that wants to open its own window.
/// The app keeps no password and never sees this one: it only learns, in a reply signed for it, that
/// the right passphrase was typed. Keys are ignored while it settles; then F2 or Enter opens, Esc
/// cancels.
final class TrustedUnlockWindow {

    final LongApproval<?> approval;
    final Recognition.Recognized code;
    final TrustedApp app;
    final PassphraseGate gate;
    final Brand brand;
    final Consumer<Boolean> onAnswer;
    final SecretField passphrase = new SecretField();
    final Label error = PassphrasePrompt.errorLabel();
    final GateFrame frame;
    final Stage stage;
    javafx.scene.control.Button open;
    Arming arming;
    boolean answered;

    TrustedUnlockWindow(LongApproval<?> approval, Recognition.Recognized code, TrustedApp app, PassphraseGate gate, Brand brand, Consumer<Boolean> onAnswer) {
        this.approval = approval;
        this.code = code;
        this.app = app;
        this.gate = gate;
        this.brand = brand;
        this.onAnswer = onAnswer;
        this.frame = new GateFrame(brand, approval.appName() + " asks to open").onClose(() -> answer(false));
        this.stage = frame.stage;
    }

    void show() {
        open = ApprovalParts.button("F2  Open " + approval.appName(), ApprovalParts.GREEN, this::submit);
        var cancel = ApprovalParts.button("Esc  Cancel", "#555", () -> answer(false));
        var armingLine = ApprovalParts.line(" ", ApprovalParts.AMBER);
        arming = new Arming(List.of(open, cancel), armingLine);
        var msg = new Label("Type " + brand.product() + "'s passphrase to open " + approval.appName() + ". "
            + approval.appName() + " never sees it; it only learns that the right one was typed. Its approval runs until "
            + TrustedParts.when(approval.expiresAt()) + ".");
        msg.setWrapText(true);
        passphrase.input().setOnAction(e -> submit());
        var root = new VBox(10, TrustedParts.header(approval.appName(), code.icon(), app == null ? TrustedParts.dev() : TrustedParts.level(app)),
            msg, passphrase, error, new HBox(8, open, cancel), armingLine);
        root.setPadding(new Insets(14));
        root.setPrefWidth(460);
        var scene = frame.scene(root);
        arming.guard(scene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!arming.armed) { e.consume(); return; }
            if (e.getCode() == KeyCode.F2) { submit(); e.consume(); }
            else if (e.getCode() == KeyCode.ESCAPE) { answer(false); e.consume(); }
        });
        stage.setTitle(brand.product().toUpperCase() + ": open " + approval.appName());
        stage.show();
        stage.toFront();
        passphrase.requestFocus();
    }

    void submit() {
        if (!arming.armed || answered || passphrase.getText().isEmpty()) return;
        PassphrasePrompt.check(gate, passphrase.getText(), open, error, () -> answer(true), passphrase::clear);
    }

    void answer(boolean ok) {
        if (answered) return;
        answered = true;
        passphrase.clear();
        onAnswer.accept(ok);
        stage.close();
    }
}
