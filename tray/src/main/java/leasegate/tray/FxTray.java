package leasegate.tray;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javafx.application.Platform;

/// A tray icon for a JavaFX app: the platform's icon, tooltip and notifications, with a TrayMenu
/// (drawn by JavaFX) on the right click and the app's own action on the left click.
/// The AWT side runs on the AWT thread and every callback comes back on the FX thread, so the app
/// only ever deals with JavaFX. A tray app should call `Platform.setImplicitExit(false)` first, or
/// JavaFX ends when its last window closes.
///
///     var menu = new TrayMenu(icon, "myapp", "#2e6da8", () -> "3 items", () -> List.of(...));
///     var tray = FxTray.install(icon, "myapp", menu, home::show);
///     tray.tooltip("myapp: 3 items");
public final class FxTray {

    static final int[] SIZES = { 16, 20, 24, 32, 40, 48, 64 };

    final TrayIcon icon;

    FxTray(TrayIcon icon) { this.icon = icon; }

    /// Puts the icon in the tray. `onLeftClick` may be null. Throws when the platform has no tray.
    public static FxTray install(javafx.scene.image.Image image, String tooltip, TrayMenu menu, Runnable onLeftClick) {
        return install(awtImage(image), tooltip, menu, onLeftClick);
    }

    /// As above, with the tray picture already made, e.g. a BaseMultiResolutionImage of hand-drawn sizes.
    public static FxTray install(Image image, String tooltip, TrayMenu menu, Runnable onLeftClick) {
        if (!SystemTray.isSupported()) throw new UnsupportedOperationException("this desktop has no system tray");
        var icon = new TrayIcon(image, tooltip);
        icon.setImageAutoSize(true);
        icon.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) {
                var x = (double) e.getXOnScreen();
                var y = (double) e.getYOnScreen();
                if (e.getButton() == MouseEvent.BUTTON1 && onLeftClick != null) Platform.runLater(onLeftClick);
                else if (e.getButton() == MouseEvent.BUTTON3 || e.isPopupTrigger()) Platform.runLater(() -> menu.toggle(x, y));
            }
        });
        onAwt(() -> {
            try { SystemTray.getSystemTray().add(icon); }
            catch (AWTException e) { throw new IllegalStateException("could not add the tray icon", e); }
        });
        Platform.runLater(menu::prewarm);
        return new FxTray(icon);
    }

    public void tooltip(String text) { onAwt(() -> icon.setToolTip(text)); }

    /// A new tray picture (e.g. on a light/dark taskbar change). See awtImage for one from JavaFX.
    public void image(Image image) { onAwt(() -> icon.setImage(image)); }

    /// A platform notification from the icon (a toast on Windows 10 and 11).
    public void message(String caption, String text) { onAwt(() -> icon.displayMessage(caption, text, TrayIcon.MessageType.INFO)); }

    public void warning(String caption, String text) { onAwt(() -> icon.displayMessage(caption, text, TrayIcon.MessageType.WARNING)); }

    public void remove() { onAwt(() -> SystemTray.getSystemTray().remove(icon)); }

    static void onAwt(Runnable r) {
        if (EventQueue.isDispatchThread()) r.run(); else EventQueue.invokeLater(r);
    }

    /// Every size the tray might ask for, drawn down from the JavaFX image in halving steps, so the
    /// 16 px icon is not a blur of a 256 px picture. Windows takes the one that fits its scale.
    public static Image awtImage(javafx.scene.image.Image fx) {
        var full = bufferedImage(fx);
        var variants = new ArrayList<Image>();
        for (var s : SIZES) variants.add(scaled(full, s));
        return new BaseMultiResolutionImage(variants.toArray(Image[]::new));
    }

    static BufferedImage bufferedImage(javafx.scene.image.Image fx) {
        var w = (int) fx.getWidth();
        var h = (int) fx.getHeight();
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var reader = fx.getPixelReader();
        for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) out.setRGB(x, y, reader.getArgb(x, y));
        return out;
    }

    static BufferedImage scaled(BufferedImage src, int size) {
        var img = src;
        while (img.getWidth() / 2 >= size) img = draw(img, img.getWidth() / 2);
        return img.getWidth() == size ? img : draw(img, size);
    }

    static BufferedImage draw(BufferedImage src, int size) {
        var out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
}
