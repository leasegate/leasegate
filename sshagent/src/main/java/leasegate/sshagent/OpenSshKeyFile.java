package leasegate.sshagent;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/// Reads an existing OpenSSH private key file ("-----BEGIN OPENSSH PRIVATE KEY-----", the
/// openssh-key-v1 format) so a key that already exists can be moved into the gate once and the
/// file deleted. Unencrypted Ed25519 only: a passphrase-protected file is refused with a clear
/// message (remove its passphrase with `ssh-keygen -p` first, then import it at once).
public final class OpenSshKeyFile {

    static final byte[] MAGIC = "openssh-key-v1\0".getBytes(StandardCharsets.US_ASCII);

    public static AgentKey parse(String text) {
        var body = text.replace("-----BEGIN OPENSSH PRIVATE KEY-----", "").replace("-----END OPENSSH PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        var raw = Base64.getDecoder().decode(body);
        if (raw.length < MAGIC.length || !Arrays.equals(Arrays.copyOf(raw, MAGIC.length), MAGIC))
            throw new IllegalArgumentException("not an OpenSSH private key file");
        var r = new SshReader(Arrays.copyOfRange(raw, MAGIC.length, raw.length));
        var cipher = r.text();
        var kdf = r.text();
        r.string();
        if (!cipher.equals("none") || !kdf.equals("none"))
            throw new IllegalArgumentException("this key file has a passphrase; remove it with ssh-keygen -p, import it, then delete the file");
        if (r.u32() != 1) throw new IllegalArgumentException("only files holding one key are supported");
        r.string();
        var priv = new SshReader(r.string());
        if (priv.u32() != priv.u32()) throw new IllegalArgumentException("the key file's check bytes differ: it is damaged");
        var type = priv.text();
        if (!type.equals(AgentKey.TYPE)) throw new IllegalArgumentException("only ssh-ed25519 keys are supported, this one is " + type);
        var pub = priv.string();
        var secret = priv.string();
        var comment = priv.text();
        return new AgentKey(Arrays.copyOf(secret, 32), pub, comment);
    }

    private OpenSshKeyFile() {}
}
