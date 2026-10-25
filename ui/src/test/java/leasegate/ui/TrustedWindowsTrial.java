package leasegate.ui;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import leasegate.core.access.PassphraseGate;
import leasegate.core.trusted.*;

import static leasegate.ui.GateWindowsTrial.*;

/// PRP 25: the trusted-app windows with nothing behind them. A dev build whose code changed and a
/// verified one side by side, then the unlock prompt, then the question asked on an explicit lock
/// (over a real long approval held by a copy of ping.exe). Screenshots each, and approves the
/// verified one with the passphrase typed, to see the decision come back.
/// Args: <shot-prefix> [an exe with a PNG icon, shown as the app's icon]
public final class TrustedWindowsTrial extends Application {

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage ignored) throws Exception {
        Platform.setImplicitExit(false);
        var raw = getParameters().getRaw();
        var prefix = raw.getFirst();
        var icon = raw.size() > 1 ? PeImage.of(Files.readAllBytes(Path.of(raw.get(1)))).largestPngIcon().orElse(null) : null;
        var gate = new PassphraseGate(Files.createTempDirectory("lg").resolve("p.json"));
        gate.setInitialPassphrase("trial1");
        var me = ProcessHandle.current();
        var start = me.info().startInstant().orElseThrow();
        var code = new Recognition.Recognized(me.pid(), start, "C:\\Apps\\example-app.exe", "a".repeat(64), "C:\\Apps\\example-app.jar", "b".repeat(64), true, icon);
        var decl = new Declaration("Example app", code.exe(), code.jar(), "https://github.com/example-org/example-app", List.of());
        var old = new TrustedApp("id", "Example app", code.exe(), "c".repeat(64), code.jar(), "b".repeat(64), null, List.of(),
            TrustedApp.Level.DEV, null, null, 1440, Instant.parse("2026-10-20T08:00:00Z"), null);
        var scope = new GateWindowsTrial.Items(Set.of("calendar", "mail"), null);

        var changed = new TrustedAppWindow<>(new TrustedAppApproval.Request<>(decl, code, Optional.of(old),
            new GitHubCheck.NotVerified("no GitHub project and release assets declared"), scope, null, 1440, true), gate, RULES, GIT, d -> {});
        changed.show();
        changed.stage.setX(20); changed.stage.setY(20);
        var decision = new Object[1];
        var verified = new TrustedAppWindow<>(new TrustedAppApproval.Request<>(decl, code, Optional.empty(),
            new GitHubCheck.Verified("example-org", "example-app", "v1.2.0", Instant.now()), scope, 240, 1440, false), gate, RULES, GIT, d -> decision[0] = d);
        verified.show();
        verified.stage.setX(680); verified.stage.setY(20);

        later(3, () -> {
            shot(prefix + "-1.png");
            verified.passphrase.setText("trial1");
            verified.approve();
            later(2, () -> {
                System.out.println("verified window decided: " + decision[0]);
                changed.withdraw();
                unlockWindow(gate, code, prefix);
            });
        });
    }

    static void unlockWindow(PassphraseGate gate, Recognition.Recognized code, String prefix) {
        var approval = new LongApproval<GateWindowsTrial.Items>("a", "id", "Example app", code.pid(), code.start(), code.exeSha256(), code.jarSha256(),
            null, Instant.now(), Instant.now().plusSeconds(8 * 3600), HeldMaterial.NONE);
        var w = new TrustedUnlockWindow(approval, code, null, gate, GIT, ok -> System.out.println("unlock answered: " + ok));
        w.show();
        later(3, () -> {
            shot(prefix + "-2.png");
            w.answer(false);
            lockQuestion(prefix);
        });
    }

    static void lockQuestion(String prefix) {
        try {
            var dir = Files.createTempDirectory("lg-trusted");
            var exe = dir.resolve("example-app.exe");
            Files.copy(Path.of(System.getenv("SystemRoot"), "System32", "PING.EXE"), exe);
            var app = new ProcessBuilder(exe.toString(), "-n", "60", "127.0.0.1").redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            Thread.sleep(300);
            var apps = new TrustedApps<>(dir, RULES, PassphraseGate.external("pw"::equals), System.out::println).human(new TrustedAppApproval<>() {
                @Override public Optional<Decision> decide(Request<GateWindowsTrial.Items> r) { return Optional.of(new Decision(480, "pw")); }
                @Override public boolean unlockFor(LongApproval<GateWindowsTrial.Items> a, Recognition.Recognized c) { return false; }
            });
            var body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("op", "request")
                .put("nonce", leasegate.transport.trusted.TrustedClient.newNonce());
            body.putObject("app").put("name", "Example app").put("exe", exe.toString());
            System.out.println("request: " + apps.handle(app.pid(), body).path("state").asText());
            later(2, () -> { shot(prefix + "-3.png"); close("long approvals are still open"); });
            Platform.runLater(() -> {
                var ended = LongApprovalsOnLock.ask(GIT, apps);
                System.out.println("ended on lock: " + ended + ", still open: " + apps.open().size());
                app.destroy();
                System.exit(0);
            });
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
