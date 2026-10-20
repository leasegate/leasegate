package leasegate.ui;

import java.util.Optional;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import leasegate.frame.Parts;
import leasegate.core.ProcInfo;
import leasegate.core.access.*;

/// The read-only parts of the approval window: who is asking, what they are about to run, and
/// exactly what they ask for. Everything is shown in full, in scrollable boxes where it is long.
/// Public, because a gate builds its own small windows from the same parts.
public final class ApprovalParts {

    public static final String RED = Parts.RED, GREEN = Parts.GREEN, GREY = Parts.GREY, AMBER = Parts.AMBER;

    static <S extends Scope<S>> Node header(Approval.Request<S> r) {
        var name = new Label(r.chain().isEmpty() ? r.app() : r.chain().getFirst().imageName());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        var badge = new Label(r.kind() == Approval.Kind.KEYED ? "KEYED APP" : "NOT APPROVED");
        badge.setStyle("-fx-background-color: " + (r.kind() == Approval.Kind.KEYED ? "#5b3fa0" : RED)
            + "; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 1 6; -fx-background-radius: 3;");
        var who = r.kind() == Approval.Kind.KEYED
            ? "app \"" + r.app() + "\" · key " + r.keyFingerprint() + " · " + (r.reason() == null ? "" : r.reason())
            : "app \"" + r.app() + "\" · PID " + r.client().pid() + " · identified by the kernel";
        var meta = small(who, GREY);
        return new VBox(3, new HBox(8, name, badge), meta,
            box("full command", ProcInfo.commandLine(r.client().pid())),
            box("working directory", ProcInfo.currentDirectory(r.client().pid())));
    }

    public static Node box(String caption, Optional<String> content) {
        var text = new TextArea(content.orElse("(unavailable)"));
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefRowCount(3);
        text.setMaxHeight(64);
        text.setFocusTraversable(false);
        text.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 10.5;");
        return new VBox(2, small(caption.toUpperCase(), "#777"), text);
    }

    /// The request, as the gate's rules describe it, plus how long it asked for.
    static <S extends Scope<S>> Node scope(S s, LeaseRules<S> rules) {
        var box = new VBox(2, small("REQUESTED", "#777"));
        for (var l : rules.describe(s)) box.getChildren().add(line(l.text(), color(l.tone())));
        box.getChildren().add(line("asked for: " + (s.requestedMinutes() == null ? "not specified" : DurationChips.label(s.requestedMinutes())), GREY));
        if (rules.needsPassphrase(s)) box.getChildren().add(line("This request reaches everything. Approving needs the passphrase.", RED));
        return box;
    }

    public static String color(ScopeLine.Tone t) {
        return switch (t) { case NARROW -> GREEN; case WIDE -> RED; case CAUTION -> AMBER; case NOTE -> GREY; };
    }

    static Node keyedNote(Approval.Request<?> r) {
        var l = new Label("This approval is held by the app's own key, not by a process. Whatever presents this key gets it,"
            + " until it expires or you revoke it. Presented this time by " + r.client().imagePath() + ".");
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 11;");
        return l;
    }

    public static Label line(String text, String color) { return Parts.line(text, color); }

    public static Label small(String text, String color) { return Parts.small(text, color); }

    public static Button button(String text, String color, Runnable action) { return Parts.button(text, color, action); }

    private ApprovalParts() {}
}
