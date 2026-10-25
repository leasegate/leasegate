package leasegate.ui;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import leasegate.core.trusted.*;

/// The pieces the trusted-app windows share: the app's own icon and name, and how far its code was
/// verified, said in one glance (a green tick naming the release, or "dev build").
final class TrustedParts {

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm, EEE d MMM").withZone(ZoneId.systemDefault());

    static String when(Instant t) { return WHEN.format(t); }

    static Node header(String name, byte[] icon, Node badge) {
        var title = new Label(name);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15;");
        var box = new HBox(10);
        if (icon != null) {
            var view = new ImageView(new Image(new ByteArrayInputStream(icon), 48, 48, true, true));
            box.getChildren().add(view);
        }
        box.getChildren().add(new VBox(4, title, badge));
        return box;
    }

    static Node verified(GitHubCheck.Verified v) {
        return badge("✔  VERIFIED  " + v.org() + "/" + v.repo() + "  " + v.tag(), "#2e7d32");
    }

    static Node dev() { return badge("DEV BUILD  locally built, verified by hash only", "#9a6700"); }

    static Node badge(String text, String color) {
        var l = new Label(text);
        l.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold;"
            + " -fx-padding: 2 8; -fx-background-radius: 3;");
        return l;
    }

    static Node level(GitHubCheck.Result check) {
        return check instanceof GitHubCheck.Verified v ? verified(v) : dev();
    }

    static Node level(TrustedApp app) {
        return app.level() == TrustedApp.Level.VERIFIED ? badge("✔  VERIFIED  " + app.releaseLabel(), "#2e7d32") : dev();
    }

    static Label mono(String text) {
        var l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 10.5; -fx-text-fill: #444;");
        return l;
    }

    private TrustedParts() {}
}
