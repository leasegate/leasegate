package leasegate.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;

import leasegate.tray.*;

import leasegate.frame.*;
import leasegate.core.LeaseEngine;
import leasegate.core.access.PassphraseGate;
import leasegate.core.access.Scope;

/// The tray every gate shares. A left click opens the gate's home window (GateHome); a right click
/// opens its menu, drawn by JavaFX (leasegate-tray), not by the platform. A gate adds its own items with
/// `item`/`addItem` before `install`, keeps them current with `onRefresh`, and says what it guards
/// with `describe`. The items are plain MenuItems: the popup and the home window both read them.
public final class TrayShell<S extends Scope<S>> {

    final Stage statusStage;
    final LeaseEngine<S> engine;
    final PassphraseGate passphraseGate;
    final AccessListWindow<S> accessList;
    final Supplier<String> statusLine;
    final Brand brand;
    final List<MenuItem> extraItems = new ArrayList<>();
    final List<Runnable> refreshers = new ArrayList<>();
    final GateHome<S> home;
    final TrayMenu popup;
    String quitLabel, tagline = "", homepage;
    Runnable beforeQuit = () -> {};
    FxTray icon;
    MenuItem blanketItem, blanketExtendItem, revokeItem;
    String tooltip = "";

    public TrayShell(Stage statusStage, LeaseEngine<S> engine, PassphraseGate passphraseGate, Brand brand, Supplier<String> statusLine) {
        this.statusStage = statusStage;
        this.engine = engine;
        this.passphraseGate = passphraseGate;
        this.brand = brand;
        this.statusLine = statusLine;
        this.accessList = new AccessListWindow<>(engine, passphraseGate, brand);
        this.quitLabel = "Quit " + product();
        this.homepage = "https://github.com/leasegate/" + product();
        this.home = new GateHome<>(this);
        this.popup = new TrayMenu(brand.icon(), product(), brand.color(), statusLine, this::menu);
        blanketItem = item(blanketLabel(), this::onBlanketModeClicked);
        blanketExtendItem = item("Extend blanket mode…", this::onBlanketExtendClicked);
        revokeItem = item("Revoke every attested approval now", () -> {
            engine.revokeAllNow();
            notify("Every attested approval revoked; keyed apps are listed in the " + product() + " window.");
        });
    }

    String product() { return engine.rules.product(); }

    public TrayShell<S> addItem(MenuItem m) { extraItems.add(m); return this; }

    public TrayShell<S> onRefresh(Runnable r) { refreshers.add(r); return this; }

    public TrayShell<S> quit(String label, Runnable before) { quitLabel = label; beforeQuit = before; return this; }

    /// One line on what this gate guards (under its name in its windows), and its home page (About).
    public TrayShell<S> describe(String tagline, String homepage) { this.tagline = tagline; this.homepage = homepage; return this; }

    public AccessListWindow<S> accessList() { return accessList; }

    public GateHome<S> home() { return home; }

    public void install() {
        icon = FxTray.install(brand.icons().tray(TaskbarTheme.isDark()), product(), popup, home::show);
        // Only the tooltip and the icon follow the state while nothing is open. A daemon thread rather
        // than an endless Timeline: a running animation keeps JavaFX's frame clock going (PRP 10).
        ticker = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().daemon().name(product() + "-tray").unstarted(r));
        ticker.scheduleWithFixedDelay(() -> Platform.runLater(this::tick), 0, 1, TimeUnit.SECONDS);
    }

    java.util.function.Consumer<String> reseal;

    /// A gate that keeps a vault: how to seal it again under a new passphrase (Vault::changePassphrase).
    public TrayShell<S> vault(java.util.function.Consumer<String> reseal) { this.reseal = reseal; return this; }

    void changePassphrase() {
        if (passphraseGate.canChange()) PassphraseChange.show(passphraseGate, brand, reseal);
        else GateFrame.tell(brand, "passphrase", product() + "'s passphrase belongs to the account it unlocks."
            + " Change it there; " + product() + " takes the new one at the next unlock.");
    }

    java.util.concurrent.ScheduledExecutorService ticker;
    final java.util.Map<String, java.awt.Image> pictures = new java.util.HashMap<>();
    String shownPicture = "";
    int ticks;

    /// The tray icon for the taskbar theme, lit by the gate's state (TrayGlow): as drawn while locked,
    /// glowing while open, glowing harder in blanket mode with a short pulse every eight seconds.
    void showState() {
        var dark = TaskbarTheme.isDark();
        var blanket = passphraseGate.isBlanketModeActive();
        var strength = blanket ? TrayGlow.BLANKET : passphraseGate.isUnlocked() ? TrayGlow.OPEN : 0;
        var key = dark + ":" + strength;
        if (!key.equals(shownPicture)) { shownPicture = key; icon.image(picture(dark, strength)); }
        if (blanket && ++ticks % 8 == 0) {
            icon.image(picture(dark, TrayGlow.PULSE));
            ticker.schedule(() -> icon.image(picture(dark, TrayGlow.BLANKET)), 700, TimeUnit.MILLISECONDS);
        }
    }

    synchronized java.awt.Image picture(boolean dark, double strength) {
        return pictures.computeIfAbsent(dark + ":" + strength, k -> TrayGlow.lit(brand.icons().tray(dark), brand.color(), strength));
    }

    public static MenuItem item(String text, Runnable action) {
        var m = new MenuItem(text);
        m.setMnemonicParsing(false);
        m.setOnAction(e -> action.run());
        return m;
    }

    public void notify(String message) { if (icon != null) icon.message(product(), message); }

    /// Changes a menu item's text only when it differs.
    public static void set(MenuItem m, String text) { if (!text.equals(m.getText())) m.setText(text); }

    void tick() {
        var line = statusLine.get();
        if (!line.equals(tooltip)) { tooltip = line; icon.tooltip(product() + ": " + line); }
        showState();
    }

    /// Brings every item up to date; run whenever the popup or the home window shows them.
    void refresh() {
        refreshers.forEach(Runnable::run);
        set(blanketItem, blanketLabel());
        blanketExtendItem.setDisable(!passphraseGate.isBlanketModeActive());
    }

    List<MenuItem> menu() {
        refresh();
        var m = new ArrayList<MenuItem>();
        m.add(item("Open the " + product() + " window", home::show));
        m.add(item("Who has access…", accessList::show));
        if (!extraItems.isEmpty()) m.add(new SeparatorMenuItem());
        m.addAll(extraItems);
        m.add(new SeparatorMenuItem());
        m.add(blanketItem);
        if (passphraseGate.isBlanketModeActive()) m.add(blanketExtendItem);
        m.add(revokeItem);
        m.add(item("Change passphrase…", this::changePassphrase));
        m.add(new SeparatorMenuItem());
        m.add(item("Check for updates…", home::checkForUpdates));
        m.add(item("About " + product(), this::about));
        m.add(new SeparatorMenuItem());
        m.add(item(quitLabel, this::quit));
        return m;
    }

    void about() { AboutWindow.show(brand, tagline, homepage); }

    String blanketLabel() {
        return passphraseGate.isBlanketModeActive()
            ? "Blanket mode ON, ends in " + hm(passphraseGate.blanketModeRemaining()) + " (click to end now)"
            : "Blanket mode for testing (up to " + DurationChips.label(PassphraseGate.BLANKET_MODE_MAX_MINUTES) + ")…";
    }

    static String hm(java.time.Duration d) {
        var m = Math.max(0, (d.toSeconds() + 59) / 60);
        return m >= 60 ? m / 60 + "h " + m % 60 + "m" : m + " min";
    }

    void onBlanketModeClicked() {
        if (passphraseGate.isBlanketModeActive()) { passphraseGate.deactivateBlanketModeNow(); refresh(); return; }
        var minutes = BlanketDurationPrompt.choose(brand);
        if (minutes == 0) return;
        var ok = PassphrasePrompt.confirmTwice(passphraseGate, brand, "blanket mode",
            "Any app, any request, with no approval window, for " + DurationChips.label(minutes)
                + ". It then ends by itself. For testing only. Type the passphrase.");
        if (ok) passphraseGate.activateBlanketMode(minutes);
        refresh();
    }

    /// Adds time while blanket mode is still on, so nobody has to wait for it to lapse and switch it on
    /// again. One passphrase entry: the two-entry friction was paid when it was switched on.
    void onBlanketExtendClicked() {
        if (!passphraseGate.isBlanketModeActive()) return;
        var minutes = BlanketDurationPrompt.choose(brand, "Add time to blanket mode, which now ends in "
            + hm(passphraseGate.blanketModeRemaining()) + ". It never runs more than "
            + DurationChips.label(PassphraseGate.BLANKET_MODE_MAX_MINUTES) + " from now.");
        if (minutes == 0) return;
        if (!PassphrasePrompt.confirmOnce(passphraseGate, brand, "extend blanket mode",
            "Add " + DurationChips.label(minutes) + " to blanket mode. Type the passphrase.")) return;
        engine.extendBlanketGrants(passphraseGate.extendBlanketMode(minutes));
        refresh();
        notify("Blanket mode now ends in " + hm(passphraseGate.blanketModeRemaining()));
    }

    void quit() {
        Thread.ofPlatform().start(() -> {
            beforeQuit.run();
            Platform.exit();
            System.exit(0);
        });
    }
}
