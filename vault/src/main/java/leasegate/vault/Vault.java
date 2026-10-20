package leasegate.vault;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/// A gate's secrets at rest. One random data key encrypts the secrets (AES-256-GCM); that key is
/// itself stored twice, each copy sealed by a different key: one derived from the passphrase
/// (PBKDF2-HMAC-SHA256), one from a recovery key shown once when the vault is created. Either opens
/// it; losing both means issuing the credentials again at their source, which is the honest cost
/// of having no copy anywhere else. The file holds nothing readable: names and values are inside
/// the encrypted part. Open means decrypted in this process's memory until `close`.
public final class Vault implements AutoCloseable {

    public record Created(Vault vault, String recoveryKey) {}

    static final ObjectMapper M = new ObjectMapper();
    static final int ITERATIONS = 600_000;
    static final String PASS = "slot:passphrase", RECOVERY = "slot:recovery", DATA = "data";
    static final Base64.Encoder B64 = Base64.getEncoder();
    static final Base64.Decoder UNB64 = Base64.getDecoder();

    final Path file;
    final ObjectNode root;
    byte[] dataKey;
    final TreeMap<String, String> secrets = new TreeMap<>();

    Vault(Path file, ObjectNode root, byte[] dataKey) {
        this.file = file;
        this.root = root;
        this.dataKey = dataKey;
    }

    public static boolean exists(Path file) { return Files.exists(file); }

    /// A new, empty vault sealed by `passphrase`, and the recovery key to write down.
    public static Created create(Path file, String passphrase) {
        if (exists(file)) throw new IllegalStateException("a vault already exists at " + file);
        var dataKey = Crypto.random(Crypto.KEY_BYTES);
        var recovery = Crypto.random(Crypto.KEY_BYTES);
        var root = M.createObjectNode().put("format", "leasegate-vault").put("v", 1);
        var v = new Vault(file, root, dataKey);
        v.sealPassphraseSlot(passphrase);
        root.put("recovery", B64.encodeToString(Crypto.seal(Crypto.fromRecovery(recovery), dataKey, RECOVERY)));
        v.save();
        return new Created(v, RecoveryKey.format(recovery));
    }

    /// Throws WrongKeyException.
    public static Vault open(Path file, String passphrase) {
        var root = read(file);
        var kdf = root.get("kdf");
        var kek = Crypto.pbkdf2(passphrase, UNB64.decode(kdf.get("salt").asText()), kdf.get("iterations").asInt());
        return load(file, root, Crypto.open(kek, UNB64.decode(root.get("passphrase").asText()), PASS));
    }

    /// Throws WrongKeyException, or IllegalArgumentException for text that is not a recovery key.
    public static Vault openWithRecovery(Path file, String recoveryKey) {
        var root = read(file);
        var kek = Crypto.fromRecovery(RecoveryKey.parse(recoveryKey));
        return load(file, root, Crypto.open(kek, UNB64.decode(root.get("recovery").asText()), RECOVERY));
    }

    /// After a recovery: the passphrase slot is sealed again under a new passphrase. The recovery
    /// key keeps working.
    public synchronized void changePassphrase(String newPassphrase) {
        sealPassphraseSlot(newPassphrase);
        save();
    }

    public synchronized Optional<String> get(String name) { return Optional.ofNullable(secrets.get(name)); }

    public synchronized Set<String> names() { return Set.copyOf(secrets.keySet()); }

    public synchronized void put(String name, String value) { secrets.put(name, value); save(); }

    public synchronized void remove(String name) { secrets.remove(name); save(); }

    /// Forgets the data key and the secrets. The file is unaffected.
    @Override public synchronized void close() {
        if (dataKey != null) Arrays.fill(dataKey, (byte) 0);
        dataKey = null;
        secrets.clear();
    }

    void sealPassphraseSlot(String passphrase) {
        var salt = Crypto.random(16);
        root.set("kdf", M.createObjectNode().put("alg", "PBKDF2WithHmacSHA256").put("iterations", ITERATIONS)
            .put("salt", B64.encodeToString(salt)));
        root.put("passphrase", B64.encodeToString(Crypto.seal(Crypto.pbkdf2(passphrase, salt, ITERATIONS), dataKey, PASS)));
    }

    static Vault load(Path file, ObjectNode root, byte[] dataKey) {
        var v = new Vault(file, root, dataKey);
        if (root.hasNonNull(DATA)) {
            try {
                var plain = Crypto.open(dataKey, UNB64.decode(root.get(DATA).asText()), DATA);
                M.readTree(new String(plain, StandardCharsets.UTF_8)).properties()
                    .forEach(e -> v.secrets.put(e.getKey(), e.getValue().asText()));
            } catch (WrongKeyException e) { throw new IllegalStateException("the vault's data does not match its key: the file is damaged", e); }
            catch (Exception e) { throw new IllegalStateException("cannot read the vault's data", e); }
        }
        return v;
    }

    static ObjectNode read(Path file) {
        try { return (ObjectNode) M.readTree(Files.readString(file)); }
        catch (Exception e) { throw new IllegalStateException("cannot read vault " + file, e); }
    }

    /// Written to a temporary file and moved over the old one, so a crash mid-write never leaves
    /// half a vault.
    void save() {
        if (dataKey == null) throw new IllegalStateException("the vault is closed");
        try {
            var plain = M.writeValueAsBytes(M.valueToTree(secrets));
            root.put(DATA, B64.encodeToString(Crypto.seal(dataKey, plain, DATA)));
            Files.createDirectories(file.getParent());
            var tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, M.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) { throw new IllegalStateException("cannot write vault " + file, e); }
    }
}
