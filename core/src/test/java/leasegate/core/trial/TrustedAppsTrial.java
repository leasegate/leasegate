package leasegate.core.trial;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import leasegate.core.access.PassphraseGate;
import leasegate.core.trusted.*;
import leasegate.core.win.RemoteProcessInfo;
import leasegate.transport.trusted.*;

import static leasegate.core.trial.Checks.check;

/// PRP 25: trusted apps end to end, with a stub human (no window). A copy of the system's ping.exe
/// stands in for a resident app: dev-level trust, a signed status, the delegated-unlock
/// acknowledgement, a code change, a refused java.exe, ending everything. With
/// `-Djrexe=<a jr exe with a baked config>` it also reads that exe's config and finds its jar.
/// java -cp <test classpath> leasegate.core.trial.TrustedAppsTrial
public final class TrustedAppsTrial {

    static final ObjectMapper M = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("leasegate-trusted-trial");
        var log = new ArrayList<String>();
        var seen = new AtomicReference<TrustedAppApproval.Request<TestScope>>();
        var apps = new TrustedApps<>(dir, TestScope.RULES, PassphraseGate.external("pw"::equals), log::add)
            .human(stub(seen)).material((app, scope, pass) -> new HeldMaterial(Map.of("token", ("for " + pass).getBytes())));
        var pub = Files.readString(apps.publicKeyFile());

        refusesJava(apps, pub);
        var exe = dir.resolve("trialapp.exe");
        Files.copy(Path.of(System.getenv("SystemRoot"), "System32", "PING.EXE"), exe);
        var app = start(exe);
        try {
            environment(app);
            devTrust(apps, pub, app, exe, seen);
            unlock(apps, pub, app);
            forgery(apps, pub, app);
        } finally { app.destroy(); app.waitFor(); }

        Files.write(exe, new byte[] {0}, StandardOpenOption.APPEND);
        var changed = start(exe);
        try { codeChange(apps, changed, exe, seen); }
        finally { changed.destroy(); changed.waitFor(); }

        githubRefusesForeignAssets();
        if (System.getProperty("jrexe") != null) jrExe(Path.of(System.getProperty("jrexe")));
        log.forEach(l -> System.out.println("  audit: " + l));
        System.exit(Checks.summary());
    }

    static TrustedAppApproval<TestScope> stub(AtomicReference<TrustedAppApproval.Request<TestScope>> seen) {
        return new TrustedAppApproval<>() {
            @Override public Optional<Decision> decide(Request<TestScope> r) { seen.set(r); return Optional.of(new Decision(480, "pw")); }
            @Override public boolean unlockFor(LongApproval<TestScope> a, Recognition.Recognized code) { return true; }
        };
    }

    static Process start(Path exe) throws Exception {
        var pb = new ProcessBuilder(exe.toString(), "-n", "120", "127.0.0.1").redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.environment().put("LEASEGATE_TRIAL", "env read back");
        var p = pb.start();
        Thread.sleep(300);
        return p;
    }

    static ObjectNode body(String op, String nonce, ObjectNode app) {
        var b = M.createObjectNode().put("op", op).put("nonce", nonce);
        if (app != null) b.set("app", app);
        b.putArray("items").add("calendar");
        return b.put("minutes", 480);
    }

    static ObjectNode app(Path exe) { return TrustedClient.app("Trial app", exe.toString(), null, null, List.of(), List.of()); }

    static void refusesJava(TrustedApps<TestScope> apps, String pub) {
        var r = apps.handle(ProcessHandle.current().pid(), body(TrustedWire.REQUEST, TrustedClient.newNonce(), null));
        check("java.exe is refused a long approval: " + r.path("reason").asText(), r.path("state").asText().equals(TrustedWire.REFUSED) && r.path("reason").asText().contains("shared runtime"));
        check("even a refusal is signed", TrustedWire.signatureValid(r, pub));
        check("a request without a fresh nonce is refused", apps.handle(1, M.createObjectNode().put("op", "status").put("nonce", "short")).path("state").asText().equals(TrustedWire.REFUSED));
    }

    static void environment(Process app) {
        var env = RemoteProcessInfo.environment(app.pid());
        check("another process's environment is read from its PEB", env.map(e -> "env read back".equals(e.get("LEASEGATE_TRIAL"))).orElse(false));
    }

    static void devTrust(TrustedApps<TestScope> apps, String pub, Process app, Path exe, AtomicReference<TrustedAppApproval.Request<TestScope>> seen) {
        var pid = app.pid();
        var s = apps.handle(pid, body(TrustedWire.STATUS, TrustedClient.newNonce(), app(exe)));
        check("an unknown app is not trusted", s.path("state").asText().equals(TrustedWire.NOT_TRUSTED));
        var nonce = TrustedClient.newNonce();
        var r = apps.handle(pid, body(TrustedWire.REQUEST, nonce, app(exe)));
        check("a request is approved as a dev build: " + r.path("state").asText(), r.path("state").asText().equals(TrustedWire.OPEN));
        check("the window was told it is a dev build, hash only", seen.get().check() instanceof GitHubCheck.NotVerified && !seen.get().codeChanged());
        check("the window was told the host is locked", seen.get().hostLocked() && r.path("hostLocked").asText().equals("true"));
        check("the reply is signed, names this nonce and pid, and the exe's hash", TrustedWire.signatureValid(r, pub)
            && r.path("nonce").asText().equals(nonce) && r.path("pid").asText().equals(Long.toString(pid))
            && r.path("exeSha256").asText().equals(Hashes.sha256(exe)));
        var a = apps.approvalFor(pid);
        check("the approval covers that process, for 8 hours", a.isPresent()
            && Math.abs(java.time.Duration.between(java.time.Instant.now(), a.get().expiresAt()).toMinutes() - 480) <= 1);
        check("its material was taken with the typed passphrase", a.flatMap(x -> x.material().get("token")).map(String::new).orElse("").equals("for pw"));
        check("status now says open", apps.handle(pid, body(TrustedWire.STATUS, TrustedClient.newNonce(), null)).path("state").asText().equals(TrustedWire.OPEN));
        check("no other process is covered by it", apps.approvalFor(ProcessHandle.current().pid()).isEmpty());
        check("the registry holds it as DEV", apps.registry().all().size() == 1 && apps.registry().all().getFirst().level() == TrustedApp.Level.DEV);
    }

    static void unlock(TrustedApps<TestScope> apps, String pub, Process app) {
        var nonce = TrustedClient.newNonce();
        var r = apps.handle(app.pid(), body(TrustedWire.UNLOCK, nonce, null));
        check("the delegated unlock answers 'unlocked', signed over this nonce", r.path("state").asText().equals(TrustedWire.UNLOCKED)
            && TrustedWire.signatureValid(r, pub) && r.path("nonce").asText().equals(nonce));
    }

    static void forgery(TrustedApps<TestScope> apps, String pub, Process app) throws Exception {
        var r = apps.handle(app.pid(), body(TrustedWire.UNLOCK, TrustedClient.newNonce(), null));
        var replayed = r.deepCopy().put("nonce", TrustedClient.newNonce());
        check("an acknowledgement replayed against another nonce fails", !TrustedWire.signatureValid(replayed, pub));
        check("one moved to another pid fails", !TrustedWire.signatureValid(r.deepCopy().put("pid", "4"), pub));
        var other = Files.createTempDirectory("leasegate-other-gate");
        var forger = new GateSigner(other);
        check("one signed by another key fails", !TrustedWire.signatureValid(forger.sign(r.deepCopy()), pub));
        apps.endAll("trial");
        check("ending it zero-fills its material and status says ended",
            apps.approvalFor(app.pid()).isEmpty() && apps.handle(app.pid(), body(TrustedWire.STATUS, TrustedClient.newNonce(), null)).path("state").asText().equals(TrustedWire.ENDED));
    }

    static void codeChange(TrustedApps<TestScope> apps, Process app, Path exe, AtomicReference<TrustedAppApproval.Request<TestScope>> seen) {
        var s = apps.handle(app.pid(), body(TrustedWire.STATUS, TrustedClient.newNonce(), app(exe)));
        check("a changed exe drops to 'code-changed'", s.path("state").asText().equals(TrustedWire.CODE_CHANGED));
        var r = apps.handle(app.pid(), body(TrustedWire.REQUEST, TrustedClient.newNonce(), app(exe)));
        check("re-trusting shows the old and new code", seen.get().codeChanged() && seen.get().previous().isPresent() && r.path("state").asText().equals(TrustedWire.OPEN));
        check("the registry keeps one entry, with the new hash", apps.registry().all().size() == 1 && apps.registry().all().getFirst().exeSha256().equals(Hashes.sha256(exe)));
    }

    static void githubRefusesForeignAssets() {
        var d = new Declaration("x", null, null, "https://github.com/example-org/app",
            List.of(new Declaration.Asset("exe", "https://github.com/someone-else/app/releases/download/v1/app.exe")));
        var r = new GitHubCheck().check(d, new Recognition.Recognized(1, java.time.Instant.now(), "x.exe", "00", null, null, false, null));
        check("an asset outside the declared repo never earns the tick", r instanceof GitHubCheck.NotVerified n && n.reason().contains("not a release download"));
    }

    static void jrExe(Path exe) throws Exception {
        var pe = PeImage.of(Files.readAllBytes(exe));
        var config = pe.named(PeImage.RT_RCDATA, "JRC").flatMap(JrConfig::parse);
        check("a jr exe's baked config is read from its bytes", config.isPresent());
        var jar = config.flatMap(c -> c.jar(exe.getParent()));
        check("and its jar found: " + jar.orElse(null), jar.isPresent());
        check("its PNG icon found", pe.largestPngIcon().isPresent());
        var running = ProcessHandle.allProcesses().filter(p -> p.info().command().map(c -> c.equalsIgnoreCase(exe.toString())).orElse(false)).findFirst();
        if (running.isEmpty()) { System.out.println("  (" + exe.getFileName() + " is not running: skipped recognizing it live)"); return; }
        var r = new Recognizer().recognize(running.get().pid(), null);
        check("the running jr app is recognized with its jar: " + r, r instanceof Recognition.Recognized g && g.jar() != null && g.jarSha256() != null);
    }
}
