package leasegate.sshagent;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/// One Ed25519 key held by the agent: the 32-byte seed (the private key), the 32-byte public key,
/// and a comment. Only the public half is ever written anywhere readable (`publicLine`, the
/// identities answer); the seed lives in the gate's vault and in this object's memory.
public final class AgentKey {

    static final String TYPE = "ssh-ed25519";
    static final byte[] PKCS8_PREFIX = HexFormat.of().parseHex("302e020100300506032b657004220420");

    final byte[] seed;
    public final byte[] publicKey;
    public final String comment;
    final PrivateKey privateKey;

    public AgentKey(byte[] seed, byte[] publicKey, String comment) {
        this.seed = seed.clone();
        this.publicKey = publicKey.clone();
        this.comment = comment;
        try {
            var pkcs8 = Arrays.copyOf(PKCS8_PREFIX, PKCS8_PREFIX.length + 32);
            System.arraycopy(seed, 0, pkcs8, PKCS8_PREFIX.length, 32);
            privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    /// A fresh key, made inside the gate, so it never exists as a file.
    public static AgentKey generate(String comment) {
        try {
            var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            var pub = pair.getPublic().getEncoded();
            var priv = pair.getPrivate().getEncoded();
            return new AgentKey(Arrays.copyOfRange(priv, priv.length - 32, priv.length), Arrays.copyOfRange(pub, pub.length - 32, pub.length), comment);
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    /// The key blob of RFC 8709: string "ssh-ed25519", string public key.
    public byte[] blob() { return new SshWriter().string(TYPE).string(publicKey).bytes(); }

    /// The line that goes into `authorized_keys`, a GitHub key setting, or a `.pub` file.
    public String publicLine() { return TYPE + " " + Base64.getEncoder().encodeToString(blob()) + (comment.isBlank() ? "" : " " + comment); }

    /// The fingerprint ssh itself prints: SHA256 of the blob, base64 without padding.
    public String fingerprint() {
        try { return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob())); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    /// The signature blob: string "ssh-ed25519", string the 64-byte signature.
    byte[] sign(byte[] data) {
        try {
            var s = Signature.getInstance("Ed25519");
            s.initSign(privateKey);
            s.update(data);
            return new SshWriter().string(TYPE).string(s.sign()).bytes();
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    /// For the vault: seed, public key and comment, as one string.
    public String toStored() {
        var b = Base64.getEncoder();
        return b.encodeToString(seed) + " " + b.encodeToString(publicKey) + " " + b.encodeToString(comment.getBytes(StandardCharsets.UTF_8));
    }

    public static AgentKey fromStored(String s) {
        var p = s.split(" ");
        var d = Base64.getDecoder();
        return new AgentKey(d.decode(p[0]), d.decode(p[1]), p.length > 2 ? new String(d.decode(p[2]), StandardCharsets.UTF_8) : "");
    }

    public boolean matches(byte[] blob) { return Arrays.equals(blob(), blob); }
}
