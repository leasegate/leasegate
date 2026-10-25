package leasegate.transport.trusted;

import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/// The app's half: build a request, and accept a reply only when the gate signed it for this
/// process and this nonce. The transport (a loopback HTTP call, a pipe) is the app's own.
///
/// The contract that goes with it: the app shows its UI only while `status` answers `open` in a
/// verified reply, and locks it again on anything else. The gate cannot enforce that; the app must.
public final class TrustedClient {

    static final SecureRandom RANDOM = new SecureRandom();
    public static final ObjectMapper M = new ObjectMapper();

    final String gatePublicKey;
    final ObjectNode app;

    /// `gatePublicKeyFile` is the gate's `trusted-apps.pub`.
    public TrustedClient(Path gatePublicKeyFile, ObjectNode app) {
        try { this.gatePublicKey = Files.readString(gatePublicKeyFile).strip(); }
        catch (Exception e) { throw new IllegalStateException("cannot read the gate's public key " + gatePublicKeyFile, e); }
        this.app = app;
    }

    /// What the app says about itself. The gate checks every part and trusts none of it: it hashes
    /// the files itself and downloads the assets itself. `jar` is null for an exe that is the app.
    public static ObjectNode app(String name, String exe, String jar, String project, List<String> exeAssets, List<String> jarAssets) {
        var a = M.createObjectNode().put("name", name).put("exe", exe);
        if (jar != null) a.put("jar", jar);
        if (project != null) a.put("project", project);
        var assets = a.putArray("assets");
        exeAssets.forEach(u -> assets.addObject().put("kind", "exe").put("url", u));
        jarAssets.forEach(u -> assets.addObject().put("kind", "jar").put("url", u));
        return a;
    }

    /// The running jr exe, as jr tells its app; empty when not started by jr.
    public static String ownExe() { return System.getProperty("io.github.jarrunner.jr.exe", ""); }

    public static String newNonce() {
        var b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    /// A request body. `extra` carries the gate's own scope fields and `minutes`, merged in.
    public ObjectNode request(String op, String nonce, ObjectNode extra) {
        var r = M.createObjectNode().put("op", op).put("nonce", nonce);
        r.set("app", app);
        if (extra != null) r.setAll(extra);
        return r;
    }

    /// True only for a reply the gate signed, to this nonce, for this very process.
    public boolean genuine(JsonNode reply, String nonce) {
        var me = ProcessHandle.current();
        var start = me.info().startInstant().map(i -> Long.toString(i.toEpochMilli())).orElse("");
        return nonce.equals(TrustedWire.text(reply, "nonce"))
            && Long.toString(me.pid()).equals(TrustedWire.text(reply, "pid"))
            && start.equals(TrustedWire.text(reply, "start"))
            && TrustedWire.signatureValid(reply, gatePublicKey);
    }

    /// Open, genuine and unexpired: the one answer on which the app may show its UI.
    public boolean open(JsonNode reply, String nonce) {
        return genuine(reply, nonce) && TrustedWire.OPEN.equals(TrustedWire.text(reply, "state"))
            && java.time.Instant.parse(TrustedWire.text(reply, "expiresAt")).isAfter(java.time.Instant.now());
    }

    /// The gate showed its passphrase prompt for this app and the right passphrase was typed.
    public boolean unlocked(JsonNode reply, String nonce) {
        return genuine(reply, nonce) && TrustedWire.UNLOCKED.equals(TrustedWire.text(reply, "state"));
    }
}
