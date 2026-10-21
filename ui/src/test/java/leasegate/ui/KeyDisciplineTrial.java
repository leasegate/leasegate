package leasegate.ui;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import leasegate.frame.*;
import leasegate.core.LeaseEngine;
import leasegate.core.access.*;
import leasegate.core.win.PeerIdentity;

import static leasegate.ui.GateWindowsTrial.GIT;
import static leasegate.ui.GateWindowsTrial.Items;
import static leasegate.ui.GateWindowsTrial.RULES;
import static leasegate.ui.KeyProbe.*;

/// PRP 17: no letter, digit, Space or Enter may answer or act in any window of the library, and the
/// function key must. Every window is opened, hit with stray keys while it arms, hit again once it
/// has settled at the focus and at every control that could hold it, and then given its accepting key.
/// java -cp <test classpath> leasegate.ui.KeyDisciplineTrial
public final class KeyDisciplineTrial {

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> {});
        Platform.setImplicitExit(false);
        go();
    }

    static void go() throws Exception {
        var dir = Files.createTempDirectory("lg");
        var gate = new PassphraseGate(dir.resolve("p.json"));
        gate.setInitialPassphrase("trial1");
        var pid = ProcessHandle.current().pid();
        var tree = ProcessTree.snapshot();
        var chain = tree.chainFrom(pid);
        var me = PeerIdentity.resolve(pid);
        var scope = new Items(Set.of("example-org/example-repo"), 60);

        var attested = fx(() -> new ApprovalWindow<>(new Approval.Request<>(Approval.Kind.ATTESTED, me, chain, tree, scope, "git", null, null), gate, RULES, GIT, d -> {}));
        var keyed = fx(() -> new ApprovalWindow<>(new Approval.Request<>(Approval.Kind.KEYED, me, chain, tree, scope, "bwgate-cli", "SHA256:example", "a key"), gate, RULES, GIT, d -> {}));
        var trust = fx(() -> new TrustWindow<>(new TrustApproval.Request<>(me, chain.getFirst(), scope, "git"), gate, RULES, GIT, d -> {}));
        var chosen = new int[1];
        var asked = new boolean[1];
        var told = new boolean[1];

        var listed = new LeaseEngine<>(RULES, r -> Optional.<Approval.Decision<Items>>empty(), new KeyedAppStore<>(dir.resolve("k1.json"), RULES));
        listed.grant(me.imagePath(), chain.getFirst(), 60, scope);
        var ending = new LeaseEngine<>(RULES, r -> Optional.<Approval.Decision<Items>>empty(), new KeyedAppStore<>(dir.resolve("k2.json"), RULES));
        var grant = ending.grant(me.imagePath(), chain.getFirst(), 60, scope);
        var before = grant.expiresAt();

        scopedKeys();
        typingKeepsItLocked(fx(() -> new ApprovalWindow<>(new Approval.Request<>(Approval.Kind.KEYED, me, chain, tree, scope, "bwgate-cli", "SHA256:example", "a key"), gate, RULES, GIT, d -> {})));

        run(List.of(
            new Case("approval, attested", attested::show, () -> attested.decided, KeyCode.F2, true),
            new Case("approval, keyed", keyed::show, () -> keyed.decided, KeyCode.F2, true),
            new Case("trust (passphrase typed in the box)", trust::show, () -> trust.decided, KeyCode.F2, true,
                () -> fx(() -> { trust.passphrase.setText("trial1"); return null; })),
            new Case("blanket mode duration", () -> chosen[0] = BlanketDurationPrompt.choose(GIT), () -> chosen[0] != 0, KeyCode.F2, true),
            new Case("yes/no question", () -> asked[0] = GateFrame.ask(GIT, "replace a key", "Replace this key?", "Replace", "Keep the old key"), () -> asked[0], KeyCode.F2, true),
            new Case("notice", () -> { GateFrame.tell(GIT, "nothing to extend", "That approval had already ended."); told[0] = true; }, () -> told[0], KeyCode.ESCAPE, false),
            new Case("about", () -> AboutWindow.show(GIT, "tagline", "https://example.com"), KeyDisciplineTrial::aboutGone, KeyCode.ESCAPE, false),
            new Case("access list", () -> new AccessListWindow<>(listed, gate, GIT).show(), () -> listed.activeGrants().isEmpty(), null, false),
            new Case("expiry warning", () -> ExpiryWarningWindow.show(ending, gate, GIT, new LeaseEngine.Expiring(grant.id(), "git", Instant.now().plusSeconds(300))),
                () -> !ending.activeGrants().getFirst().expiresAt().equals(before), KeyCode.F2, true)));
    }

    /// A sentence typed over six seconds, one key every 1.4 s: the window must still be locked a moment
    /// after the last key, and open to F2 only after two quiet seconds.
    static void typingKeepsItLocked(ApprovalWindow<Items> w) {
        Platform.runLater(w::show);
        sleep(1000);
        for (var i = 0; i < 5; i++) { fx(() -> { fire(target(w.stage), KeyCode.A); return null; }); sleep(1400); }
        fx(() -> { fire(target(w.stage), KeyCode.F2); return null; });
        sleep(300);
        check("typing for six seconds keeps the window locked: F2 straight after the last key does nothing", !w.decided && w.stage.isShowing());
        sleep(2500);
        fx(() -> { fire(target(w.stage), KeyCode.F2); return null; });
        sleep(500);
        check("after two quiet seconds F2 acts", w.decided);
    }

    /// Keys bound to one part of a window (a tab) act only while that part is showing, so two parts can
    /// both bind F2; a key bound without a button still refuses a typed key; mouseOnly reaches every
    /// button however deeply it sits. Everything here runs on the FX thread, so nothing waits on timing.
    static void scopedKeys() {
        fx(() -> {
            var hits = new int[2];
            var first = new javafx.scene.layout.VBox();
            var second = new javafx.scene.layout.VBox();
            second.setVisible(false);
            new FKeys().bind(KeyCode.F2, () -> hits[0]++).install(first);
            new FKeys().bind(KeyCode.F2, () -> hits[1]++).install(second);
            var stage = new Stage();
            stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.StackPane(first, second), 200, 100));
            var root = stage.getScene().getRoot();

            fire(root, KeyCode.F2);
            check("a part's key does nothing while its window is not showing", hits[0] == 0 && hits[1] == 0);
            stage.show();
            fire(root, KeyCode.F2);
            check("F2 acts for the part that is showing, and not for the hidden one", hits[0] == 1 && hits[1] == 0);
            first.setVisible(false);
            second.setVisible(true);
            fire(root, KeyCode.F2);
            check("once the other part shows, F2 acts for it instead", hits[0] == 1 && hits[1] == 1);
            fire(root, KeyCode.A);
            fire(root, KeyCode.ENTER);
            check("a letter or Enter acts for neither part", hits[0] == 1 && hits[1] == 1);
            stage.close();

            var refused = false;
            try { new FKeys().bind(KeyCode.A, () -> {}); } catch (IllegalArgumentException e) { refused = true; }
            check("a typed key cannot be bound, even without a button", refused);

            var deep = new javafx.scene.control.Button("deep");
            var top = new javafx.scene.control.CheckBox("top");
            var tree = new javafx.scene.layout.VBox(top, new javafx.scene.layout.HBox(new javafx.scene.layout.VBox(deep)));
            Parts.mouseOnly(tree);
            check("mouseOnly takes every button out of the focus order, however deep", !deep.isFocusTraversable() && !top.isFocusTraversable());
            return null;
        });
    }

    static boolean aboutGone() {
        return showing().stream().noneMatch(w -> w instanceof Stage s && s.getTitle() != null && s.getTitle().endsWith(": about"));
    }
}
