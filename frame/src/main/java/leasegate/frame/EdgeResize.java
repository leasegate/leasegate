package leasegate.frame;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/// Resizing a GLOW window from its edges and corners, which the platform does not do for a
/// transparent window. The grab zone straddles the visible card's border; the halo margin around
/// it stays click-through.
final class EdgeResize {

    final Stage stage;
    final Node card;
    int edges;                       // bit set of N, S, W, E
    static final int N = 1, S = 2, W = 4, E = 8;
    double startX, startY, startW, startH, pressX, pressY;

    EdgeResize(Stage stage, Node card) {
        this.stage = stage;
        this.card = card;
    }

    void install(Node root) {
        root.addEventFilter(MouseEvent.MOUSE_MOVED, e -> root.setCursor(cursor(edgesAt(e))));
        root.addEventFilter(MouseEvent.MOUSE_EXITED, e -> root.setCursor(Cursor.DEFAULT));
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            edges = edgesAt(e);
            if (edges == 0) return;
            pressX = e.getScreenX(); pressY = e.getScreenY();
            startX = stage.getX(); startY = stage.getY(); startW = stage.getWidth(); startH = stage.getHeight();
            e.consume();
        });
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (edges == 0) return;
            double dx = e.getScreenX() - pressX, dy = e.getScreenY() - pressY;
            double minW = card.minWidth(-1) + 2 * GateFrame.HALO, minH = card.minHeight(-1) + 2 * GateFrame.HALO;
            if ((edges & E) != 0) stage.setWidth(Math.max(minW, startW + dx));
            if ((edges & S) != 0) stage.setHeight(Math.max(minH, startH + dy));
            if ((edges & W) != 0) { var w = Math.max(minW, startW - dx); stage.setX(startX + startW - w); stage.setWidth(w); }
            if ((edges & N) != 0) { var h = Math.max(minH, startH - dy); stage.setY(startY + startH - h); stage.setHeight(h); }
            e.consume();
        });
        root.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> { if (edges != 0) { edges = 0; e.consume(); } });
    }

    int edgesAt(MouseEvent e) {
        var b = card.localToScene(card.getBoundsInLocal());
        double x = e.getSceneX(), y = e.getSceneY(), z = GateFrame.EDGE;
        if (x < b.getMinX() - z || x > b.getMaxX() + z || y < b.getMinY() - z || y > b.getMaxY() + z) return 0;
        int r = 0;
        if (Math.abs(y - b.getMinY()) <= z) r |= N;
        if (Math.abs(y - b.getMaxY()) <= z) r |= S;
        if (Math.abs(x - b.getMinX()) <= z) r |= W;
        if (Math.abs(x - b.getMaxX()) <= z) r |= E;
        return r;
    }

    static Cursor cursor(int edges) {
        return switch (edges) {
            case N, S -> Cursor.V_RESIZE;
            case W, E -> Cursor.H_RESIZE;
            case N | W, S | E -> Cursor.NW_RESIZE;
            case N | E, S | W -> Cursor.NE_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }
}
