package leasegate.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Optional;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/// PRP 06 sample: the same approval-like content in three frames, side by side, so the frame is the
/// only difference. Left: today's UTILITY window. Middle: UNDECORATED, the brand band drawn as the
/// frame (drag it, ✕ closes as a denial). Right: EXTENDED with a HeaderBar (JavaFX 25 preview),
/// the band is the title bar and Windows keeps its own caption buttons, shadow and snap.
/// Run with -Djavafx.enablePreview=true. Optional first arg: a png path to screenshot the three to.
public final class FrameStylesTrial extends Application {

    static final Brand GIT = new Brand("gitgate", "#2e7d32", GateIcon.image("#2e7d32"));
    static final Brand SSH = new Brand("sshgate", "#795548", GateIcon.image("#795548"));
    static final Brand CDP = new Brand("cdpgate", "#1565c0", GateIcon.image("#1565c0"));

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage ignored) {
        var a = current(GIT);
        var b = undecorated(SSH);
        var c = extended(CDP);
        var stages = new Stage[] { a, b, c };
        var backdrop = new Stage(StageStyle.UNDECORATED);
        var screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        var plain = new Pane();
        plain.setStyle("-fx-background-color: #d9d9d9;");
        backdrop.setScene(new Scene(plain, screen.getWidth(), screen.getHeight()));
        backdrop.setX(screen.getMinX());
        backdrop.setY(screen.getMinY());
        backdrop.show();
        double x = screen.getMinX() + 40;
        for (var s : stages) {
            s.setX(x);
            s.setY(60);
            s.setAlwaysOnTop(true);
            s.show();
            s.toFront();
            x += s.getWidth() + 30;
        }
        var shot = getParameters().getRaw();
        if (!shot.isEmpty()) {
            var pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
            pause.setOnFinished(e -> {
                double minX = Double.MAX_VALUE, maxX = 0, maxY = 0;
                for (var s : stages) { minX = Math.min(minX, s.getX()); maxX = Math.max(maxX, s.getX() + s.getWidth()); maxY = Math.max(maxY, s.getY() + s.getHeight()); }
                capture(new Rectangle2D(minX - 16, 44, maxX - minX + 32, maxY - 44 + 16), new File(shot.getFirst()));
                Platform.exit();
            });
            pause.play();
        }
    }

    /// What every window looks like today: platform title bar, then the band inside the content.
    static Stage current(Brand brand) {
        var stage = new Stage(StageStyle.UTILITY);
        var root = new VBox(10, brand.band("approval request: a process asks for access"), body());
        root.setPadding(new Insets(14));
        root.setPrefWidth(380);
        stage.setTitle(brand.product().toUpperCase() + ": request from git");
        stage.getIcons().add(brand.icon());
        stage.setScene(new Scene(root));
        escCloses(stage);
        return stage;
    }

    /// The band is the frame: no platform title bar at all. A brand-coloured border keeps the window's
    /// edge visible on any desktop, since an undecorated window gets no shadow.
    static Stage undecorated(Brand brand) {
        var stage = new Stage(StageStyle.UNDECORATED);
        var close = closeButton(stage);
        var band = new BorderPane();
        band.setCenter(bandContent(brand, "approval request: a process asks for access"));
        band.setRight(close);
        BorderPane.setAlignment(close, Pos.TOP_RIGHT);
        band.setStyle("-fx-background-color: " + brand.color() + ";");
        dragWith(band, stage);

        var content = body();
        VBox.setMargin(content, new Insets(12, 14, 14, 14));
        var root = new VBox(band, content);
        root.setPrefWidth(380);
        root.setStyle("-fx-border-color: " + brand.color() + "; -fx-border-width: 3; -fx-background-color: white;");
        stage.setTitle(brand.product().toUpperCase() + ": request from ssh");
        stage.getIcons().add(brand.icon());
        stage.setScene(new Scene(root));
        escCloses(stage);
        return stage;
    }

    /// The band is the title bar, but Windows still draws the frame: its own minimise/close
    /// buttons, the drop shadow, snap and the system menu. JavaFX 25 preview API.
    static Stage extended(Brand brand) {
        var stage = new Stage(StageStyle.EXTENDED);
        var header = new HeaderBar();
        header.setLeading(bandContent(brand, "approval request: a process asks for access"));
        header.setStyle("-fx-background-color: " + brand.color() + ";");

        var content = body();
        VBox.setMargin(content, new Insets(12, 14, 14, 14));
        var root = new VBox(header, content);
        root.setPrefWidth(380);
        root.setStyle("-fx-background-color: white;");
        stage.setTitle(brand.product().toUpperCase() + ": request from chrome");
        stage.getIcons().add(brand.icon());
        stage.setScene(new Scene(root));
        escCloses(stage);
        return stage;
    }

    static Node bandContent(Brand brand, String subtitle) {
        var view = new ImageView(brand.icon());
        view.setFitWidth(40);
        view.setFitHeight(40);
        var plate = new StackPane(view);
        plate.setPadding(new Insets(3));
        plate.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        var name = new Label(brand.product().toUpperCase());
        name.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: white;");
        var sub = new Label(subtitle);
        sub.setStyle("-fx-font-size: 11; -fx-text-fill: white;");
        var box = new HBox(12, plate, new VBox(0, name, sub));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8, 12, 8, 12));
        return box;
    }

    static Button closeButton(Stage stage) {
        var b = new Button("✕");
        b.setFocusTraversable(false);
        b.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 16; -fx-padding: 6 14;");
        b.setOnMouseEntered(e -> b.setStyle(b.getStyle().replace("transparent", "#c42b1c")));
        b.setOnMouseExited(e -> b.setStyle(b.getStyle().replace("#c42b1c", "transparent")));
        b.setOnAction(e -> stage.close());
        return b;
    }

    static void dragWith(Node handle, Stage stage) {
        double[] at = new double[2];
        handle.setOnMousePressed(e -> { at[0] = e.getScreenX() - stage.getX(); at[1] = e.getScreenY() - stage.getY(); });
        handle.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - at[0]); stage.setY(e.getScreenY() - at[1]); });
    }

    static void escCloses(Stage stage) {
        stage.sceneProperty().addListener((o, was, s) -> s.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) stage.close(); }));
    }

    /// Stand-in for the approval body, built from the real parts so it reads the same.
    static Node body() {
        var scope = new VBox(2, ApprovalParts.small("REQUESTED", "#777"),
            ApprovalParts.line("push to example-org/example-repo", ApprovalParts.GREEN),
            ApprovalParts.line("asked for: 1 h", ApprovalParts.GREY));
        var buttons = new HBox(8, ApprovalParts.button("F2  Approve", ApprovalParts.GREEN, () -> {}),
            ApprovalParts.button("Esc  Deny", ApprovalParts.RED, () -> {}));
        return new VBox(10, ApprovalParts.box("full command", Optional.of("git push origin main")),
            ApprovalParts.box("working directory", Optional.of("D:\\work\\example-repo")),
            scope, buttons, ApprovalParts.small("↑/↓ anchor · ←/→ duration · F2 approve · Esc deny", "#777"));
    }

    static void capture(Rectangle2D area, File out) {
        var img = new Robot().getScreenCapture(null, area);
        var pr = img.getPixelReader();
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        var bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
        try { ImageIO.write(bi, "png", out); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
