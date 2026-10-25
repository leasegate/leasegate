package leasegate.core.trusted;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import leasegate.transport.trusted.TrustedWire;

/// The gate's Ed25519 key for trusted-app replies: made on first use, the private half in
/// `trusted-apps-key.json`, the public half also in `trusted-apps.pub`, which is all an app reads.
/// An answer without this signature, over this nonce and this caller, is worth nothing to the app,
/// so a process squatting the gate's address, or replaying an older answer, gets nowhere.
public final class GateSigner {

    static final ObjectMapper M = new ObjectMapper();

    final PrivateKey key;
    public final String publicKey;
    public final Path publicKeyFile;

    public GateSigner(Path dir) {
        var file = dir.resolve("trusted-apps-key.json");
        publicKeyFile = dir.resolve("trusted-apps.pub");
        try {
            if (!Files.exists(file)) create(file);
            var n = M.readTree(Files.readString(file));
            key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(n.get("private").asText())));
            publicKey = n.get("public").asText();
            if (!Files.exists(publicKeyFile) || !Files.readString(publicKeyFile).strip().equals(publicKey)) Files.writeString(publicKeyFile, publicKey);
        } catch (Exception e) { throw new IllegalStateException("cannot load the trusted-app key in " + dir, e); }
    }

    void create(Path file) throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.createDirectories(file.getParent());
        var b64 = Base64.getEncoder();
        Files.writeString(file, M.createObjectNode().put("alg", "Ed25519").put("private", b64.encodeToString(pair.getPrivate().getEncoded()))
            .put("public", b64.encodeToString(pair.getPublic().getEncoded())).toPrettyString());
    }

    /// Adds `sig` over the reply's canonical form.
    public ObjectNode sign(ObjectNode reply) {
        try {
            var s = Signature.getInstance("Ed25519");
            s.initSign(key);
            s.update(TrustedWire.canonical(reply).getBytes(StandardCharsets.UTF_8));
            return reply.put("sig", Base64.getEncoder().encodeToString(s.sign()));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
