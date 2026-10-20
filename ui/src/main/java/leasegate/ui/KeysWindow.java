package leasegate.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import leasegate.sshagent.AgentKey;
import leasegate.sshagent.OpenSshKeyFile;
import leasegate.sshagent.VaultKeys;

/// A gate's SSH keys, one per entry (an account, a server): its fingerprint and public line, and
/// two ways to get one in -- made here, so it never exists as a file, or imported once from an
/// existing key file, which should then be deleted. Replacing a key asks first, because the old one
/// stops working the moment it is replaced. `whereToPut` tells the human where the public half goes.
public final class KeysWindow {

    public record Entry(String id, String label) {}

    public static void show(String product, List<Entry> entries, VaultKeys keys, String whereToPut, Runnable changed) {
        var stage = new Stage();
        var list = new ListView<String>(FXCollections.observableArrayList(entries.stream().map(e -> e.id() + " · " + e.label()).toList()));
        list.setPrefHeight(Math.min(160, 30 + entries.size() * 24));
        var pub = new TextArea();
        pub.setEditable(false);
        pub.setWrapText(true);
        pub.setPrefRowCount(4);
        pub.setStyle("-fx-font-family: Consolas, monospace;");
        var path = new TextField();
        path.setPromptText("path of a key file to import, then F4");
        var message = new Label(" ");
        message.setWrapText(true);
        Runnable refresh = () -> {
            var i = list.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            pub.setText(keys.get(entries.get(i).id()).map(k -> k.fingerprint() + "\n" + k.publicLine()).orElse("(no key yet: F3 makes one, or F4 imports a key file)"));
        };
        list.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> refresh.run());
        list.getSelectionModel().selectFirst();
        Runnable copy = () -> {
            var k = keys.get(entries.get(list.getSelectionModel().getSelectedIndex()).id());
            if (k.isEmpty()) return;
            var c = new ClipboardContent();
            c.putString(k.get().publicLine());
            Clipboard.getSystemClipboard().setContent(c);
            say(message, "Public key copied. " + whereToPut, ApprovalParts.GREY);
        };
        Runnable make = () -> {
            var id = entries.get(list.getSelectionModel().getSelectedIndex()).id();
            if (keys.get(id).isPresent() && !confirm(product, "Replace " + id + "'s key? The old key stops working through " + product + " at once.")) return;
            keys.put(id, AgentKey.generate(id + "@" + product));
            refresh.run();
            changed.run();
            copy.run();
            say(message, "A new key, made inside " + product + ", is copied. " + whereToPut + " Then remove the old key there.", ApprovalParts.GREEN);
        };
        Runnable importFile = () -> {
            var id = entries.get(list.getSelectionModel().getSelectedIndex()).id();
            try {
                var file = Path.of(path.getText().trim());
                var k = OpenSshKeyFile.parse(Files.readString(file));
                if (keys.get(id).isPresent() && !confirm(product, "Replace " + id + "'s key with this one?")) return;
                keys.put(id, k);
                refresh.run();
                changed.run();
                say(message, "Imported " + k.fingerprint() + ". It is in the vault now; delete " + file + " once it works through " + product + ".", ApprovalParts.GREEN);
            } catch (Exception e) { say(message, String.valueOf(e.getMessage()), ApprovalParts.RED); }
        };
        var fkeys = new FKeys().bind(KeyCode.F2, "Copy public key", "#2e6da8", copy).bind(KeyCode.F3, "Make a new key", "#5b3fa0", make)
            .bind(KeyCode.F4, "Import the key file", "#8a4b00", importFile).bind(KeyCode.ESCAPE, "Close", "#777", stage::close);
        var root = new VBox(10, new Label("Keys"), list, new Label("The public half (the private half never leaves " + product + ")"), pub,
            path, message, fkeys.bar, ApprovalParts.small("Arrows choose · F2 copy · F3 make · F4 import the file named above · Esc closes", "#777"));
        root.setPadding(new Insets(14));
        root.setPrefWidth(640);
        var scene = new Scene(root);
        fkeys.install(scene);
        stage.setTitle(product + ": SSH keys");
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.show();
        list.requestFocus();
    }

    static void say(Label l, String text, String color) {
        l.setText(text);
        l.setStyle("-fx-text-fill: " + color + ";");
    }

    static boolean confirm(String product, String text) {
        var a = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
        a.setTitle(product);
        a.setHeaderText(null);
        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    private KeysWindow() {}
}
