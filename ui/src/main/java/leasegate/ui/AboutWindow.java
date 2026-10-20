package leasegate.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import jarrunner.jr.apputils.JrApp;
import leasegate.frame.*;

/// Which gate this is, which build, what it runs on, and where its home page is. Enter or Esc closes.
public final class AboutWindow {

    public static void show(Brand brand, String tagline, String homepage) {
        var f = new GateFrame(brand, "about").notOnTop();
        var grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(4);
        var r = 0;
        r = fact(grid, r, "version", UpdateCheck.version());
        r = fact(grid, r, "update channel", JrApp.launchedByJr() ? JrApp.updateChannel() : "none (not started from its exe)");
        r = fact(grid, r, "exe", JrApp.exe().map(Object::toString).orElse("none"));
        r = fact(grid, r, "Java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        r = fact(grid, r, "JavaFX", System.getProperty("javafx.runtime.version", "?"));
        fact(grid, r, "OS", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        var link = new Hyperlink(homepage);
        link.setMnemonicParsing(false);
        link.setFocusTraversable(false);
        link.setOnAction(e -> open(homepage));
        var note = Parts.small("One of the leasegate gates: it holds credentials and lets a program use them for a limited "
            + "time, and only after a person has approved that program in its window.", "#555");
        var ok = Parts.button("Enter  Close", "#555", f.stage::close);
        ok.setDefaultButton(true);
        ok.setCancelButton(true);
        var body = new VBox(14, brand.logo(96, tagline), grid, note, link, ok);
        body.setPadding(new Insets(18));
        body.setPrefWidth(520);
        var scene = f.scene(body);
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) f.stage.close(); });
        f.stage.show();
    }

    static int fact(GridPane grid, int row, String name, String value) {
        var n = Parts.small(name, "#777");
        var v = new Label(value);
        v.setWrapText(true);
        v.setStyle("-fx-font-size: 12;");
        grid.addRow(row, n, v);
        return row + 1;
    }

    static void open(String url) {
        Thread.ofPlatform().daemon().start(() -> {
            try { java.awt.Desktop.getDesktop().browse(java.net.URI.create(url)); } catch (Exception e) { }
        });
    }

    private AboutWindow() {}
}
