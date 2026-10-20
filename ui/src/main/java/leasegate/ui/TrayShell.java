package leasegate.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;

import com.dustinredmond.fxtrayicon.FXTrayIcon;

import leasegate.core.LeaseEngine;
import leasegate.core.access.PassphraseGate;
import leasegate.core.access.Scope;

/// The tray every gate shares: its state line, who has access, the gate's own items, blanket mode
/// for testing (up to four hours), revoke everything, and quit. A gate adds its own items with
/// `item`/`addItem` before `install`, and keeps them current with `onRefresh`.
public final class TrayShell<S extends Scope<S>> {

    final Stage statusStage;
    final LeaseEngine<S> engine;
    final PassphraseGate passphraseGate;
    final AccessListWindow<S> accessList;
    final Supplier<String> statusLine;
    final Brand brand;
    final List<MenuItem> extraItems = new ArrayList<>();
    final List<Runnable> refreshers = new ArrayList<>();
    String quitLabel;
    Runnable beforeQuit = () -> {};
    FXTrayIcon icon;
    MenuItem stateItem, blanketItem, blanketExtendItem;
    String tooltip = "";

    public TrayShell(Stage statusStage, LeaseEngine<S> engine, PassphraseGate passphraseGate, Brand brand, Supplier<String> statusLine) {
        this.statusStage = statusStage;
        this.engine = engine;
        this.passphraseGate = passphraseGate;
        this.brand = brand;
        this.statusLine = statusLine;
        this.accessList = new AccessListWindow<>(engine, passphraseGate, brand);
        this.quitLabel = "Quit " + product();
    }

    String product() { return engine.rules.product(); }

    public TrayShell<S> addItem(MenuItem m) { extraItems.add(m); return this; }

    public TrayShell<S> onRefresh(Runnable r) { refreshers.add(r); return this; }

    public TrayShell<S> quit(String label, Runnable before) { quitLabel = label; beforeQuit = before; return this; }

    public AccessListWindow<S> accessList() { return accessList; }

    public void install() {
        icon = new FXTrayIcon(statusStage, brand.icon());
        icon.noDefaultAction();
        icon.setOnAction(e -> accessList.show());
        // FXTrayIcon refuses a label already in the menu, on the AWT thread where nobody sees the
        // exception, so every item needs its real text before it is added; two empty ones drop the second.
        stateItem = new MenuItem(statusLine.get());
        stateItem.setDisable(true);
        icon.addMenuItem(stateItem);
        icon.addMenuItem(new SeparatorMenuItem());
        icon.addMenuItem(item("Who has access…", accessList::show));
        extraItems.forEach(icon::addMenuItem);
        icon.addMenuItem(new SeparatorMenuItem());
        blanketItem = item(blanketLabel(), this::onBlanketModeClicked);
        icon.addMenuItem(blanketItem);
        blanketExtendItem = item("Extend blanket mode…", this::onBlanketExtendClicked);
        icon.addMenuItem(blanketExtendItem);
        icon.addMenuItem(item("Revoke every attested approval now", () -> {
            engine.revokeAllNow();
            icon.showInfoMessage(product(), "Every attested approval revoked; keyed apps are listed under Who has access.");
        }));
        icon.addMenuItem(new SeparatorMenuItem());
        icon.addMenuItem(item(quitLabel, this::quit));
        icon.show();
        var ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> refresh()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
        refresh();
    }

    public static MenuItem item(String text, Runnable action) {
        var m = new MenuItem(text);
        m.setMnemonicParsing(false);
        m.setOnAction(e -> action.run());
        return m;
    }

    public void notify(String message) { Platform.runLater(() -> icon.showInfoMessage(product(), message)); }

    /// Ticks every second but touches the menu only when something it shows has changed: every change
    /// rebuilds the native popup, and rewriting it each second is what made it flicker.
    /// The blanket countdown is shown to the minute for the same reason.
    void refresh() {
        var line = statusLine.get();
        var blanketOn = passphraseGate.isBlanketModeActive();
        set(stateItem, line);
        if (!line.equals(tooltip)) { tooltip = line; icon.setTrayIconTooltip(product() + ": " + line); }
        refreshers.forEach(Runnable::run);
        set(blanketItem, blanketLabel());
        if (blanketExtendItem.isDisable() == blanketOn) blanketExtendItem.setDisable(!blanketOn);
    }

    String blanketLabel() {
        return passphraseGate.isBlanketModeActive()
            ? "Blanket mode ON, ends in " + hm(passphraseGate.blanketModeRemaining()) + " (click to end now)"
            : "Blanket mode for testing (up to " + DurationChips.label(PassphraseGate.BLANKET_MODE_MAX_MINUTES) + ")…";
    }

    /// Changes a menu item's text only when it differs (see `refresh`).
    public static void set(MenuItem m, String text) { if (!text.equals(m.getText())) m.setText(text); }

    static String hm(java.time.Duration d) {
        var m = Math.max(0, (d.toSeconds() + 59) / 60);
        return m >= 60 ? m / 60 + "h " + m % 60 + "m" : m + " min";
    }

    void onBlanketModeClicked() {
        if (passphraseGate.isBlanketModeActive()) { passphraseGate.deactivateBlanketModeNow(); refresh(); return; }
        var minutes = BlanketDurationPrompt.choose(product());
        if (minutes == 0) return;
        var ok = PassphrasePrompt.confirmTwice(passphraseGate, product() + ": blanket mode",
            "Any app, any request, with no approval window, for " + DurationChips.label(minutes)
                + ". It then ends by itself. For testing only. Type the passphrase.");
        if (ok) passphraseGate.activateBlanketMode(minutes);
        refresh();
    }

    /// Adds time while blanket mode is still on, so nobody has to wait for it to lapse and switch it on
    /// again. One passphrase entry: the two-entry friction was paid when it was switched on.
    void onBlanketExtendClicked() {
        if (!passphraseGate.isBlanketModeActive()) return;
        var minutes = BlanketDurationPrompt.choose(product(), "Add time to blanket mode, which now ends in "
            + hm(passphraseGate.blanketModeRemaining()) + ". It never runs more than "
            + DurationChips.label(PassphraseGate.BLANKET_MODE_MAX_MINUTES) + " from now.");
        if (minutes == 0) return;
        if (!PassphrasePrompt.confirmOnce(passphraseGate, product() + ": extend blanket mode",
            "Add " + DurationChips.label(minutes) + " to blanket mode. Type the passphrase.")) return;
        engine.extendBlanketGrants(passphraseGate.extendBlanketMode(minutes));
        refresh();
        icon.showInfoMessage(product(), "Blanket mode now ends in " + hm(passphraseGate.blanketModeRemaining()));
    }

    void quit() {
        Thread.ofPlatform().start(() -> {
            beforeQuit.run();
            Platform.exit();
            System.exit(0);
        });
    }
}
