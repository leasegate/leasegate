package leasegate.transport.trusted;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;

/// The trusted-app protocol as both ends see it. A request is `{"op":..,"nonce":..,"app":{..},..}`;
/// every reply is signed by the gate over `canonical(reply)`, which names the app's own nonce, the
/// kernel-named caller (pid and start time) and the code the gate hashed. So a reply is good for
/// exactly one question from exactly one process, and only the gate's key can make one.
public final class TrustedWire {

    public static final String
        STATUS = "status", REQUEST = "request", UNLOCK = "unlock",
        OPEN = "open", NOT_TRUSTED = "not-trusted", CODE_CHANGED = "code-changed", EXPIRED = "expired",
        ENDED = "ended", NOT_OPEN = "not-open", REFUSED = "refused", UNSUPPORTED = "unsupported",
        DECLINED = "declined", PENDING = "pending", UNLOCKED = "unlocked";

    static final String VERSION = "leasegate-trusted-v1";

    /// The signed text: fixed fields in a fixed order, one per line, absent ones empty.
    public static String canonical(JsonNode r) {
        return String.join("\n", VERSION, text(r, "op"), text(r, "state"), text(r, "nonce"), text(r, "pid"),
            text(r, "start"), text(r, "exeSha256"), text(r, "jarSha256"), text(r, "expiresAt"),
            text(r, "hostLocked"), text(r, "issuedAt"));
    }

    static String text(JsonNode r, String field) { return r.hasNonNull(field) ? r.get(field).asText() : ""; }

    public static boolean signatureValid(JsonNode reply, String publicKeyB64) {
        try {
            var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64.strip())));
            var s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(canonical(reply).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return s.verify(Base64.getDecoder().decode(text(reply, "sig")));
        } catch (Exception e) { return false; }
    }

    private TrustedWire() {}
}
