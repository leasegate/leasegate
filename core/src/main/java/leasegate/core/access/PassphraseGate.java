package leasegate.core.access;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Predicate;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;

/// Password gating on top of everything else a gate already enforces (kernel attestation, scope
/// enforcement, duration caps). Three tiers, each additive:
///
/// - **Tier 0 (baseline unlock)**: a passphrase (any characters, not blank), set on first run, entered
///   once to unlock the tray app -- nothing else starts until this succeeds.
/// - **Tier 1 (per-connection re-confirm)**: a request the gate's rules call too broad
///   (`LeaseRules.needsPassphrase`) re-asks the passphrase before Approve is clickable.
/// - **Tier 2 (blanket mode, capped)**: a separate, explicit "trust everything" toggle, confirmed
///   TWICE and for a duration the human picks, at most `BLANKET_MODE_MAX_MINUTES` regardless of
///   anything else -- auto-reverts, never renewed just by staying active. For testing only.
///
/// Stores only a salted PBKDF2-SHA256 hash; the plain passphrase is never persisted. A file written
/// by an earlier version holds a single salted SHA-256 instead: it is still accepted, and the next
/// successful unlock writes a PBKDF2 copy beside it (`upgradedPath`), which is read from then on. There is no reset from here: a gate that also keeps a
/// vault can reset the passphrase after its recovery key has been proved (`resetAfterRecovery`).
public final class PassphraseGate {

    public static final int BLANKET_MODE_MAX_MINUTES = 240;
    public static final int[] BLANKET_MODE_CHOICES = {30, 60, 120, 240};
    static final int ITERATIONS = 600_000;

    final Path storePath;
    final Predicate<String> external;
    final ObjectMapper mapper = new ObjectMapper();
    String saltHex;
    String hashHex;
    String kind;
    String hint = "";
    volatile boolean unlocked;
    volatile Instant blanketModeExpiresAt;

    public PassphraseGate(Path storePath) {
        this.storePath = storePath;
        this.external = null;
        load();
    }

    PassphraseGate(Predicate<String> check) {
        this.storePath = null;
        this.external = check;
    }

    /// A gate whose passphrase belongs to something else (a password manager's master password):
    /// no hash file, nothing to set up or reset; `check` answers every unlock and re-confirm.
    public static PassphraseGate external(Predicate<String> check) { return new PassphraseGate(check); }

    public boolean hasPassphrase() { return external != null || hashHex != null; }

    public boolean isUnlocked() { return unlocked; }

    public static boolean isValidPassphrase(String candidate) {
        return candidate != null && !candidate.isBlank();
    }

    /// First run only: sets the passphrase and unlocks in the same act. Refuses if one is already set.
    public synchronized boolean setInitialPassphrase(String passphrase) {
        if (hasPassphrase() || !isValidPassphrase(passphrase)) return false;
        store(passphrase);
        unlocked = true;
        return true;
    }

    /// Only for a gate that has just proved its recovery key: replaces the passphrase and unlocks.
    public synchronized boolean resetAfterRecovery(String newPassphrase) {
        if (external != null || !isValidPassphrase(newPassphrase)) return false;
        store(newPassphrase);
        unlocked = true;
        return true;
    }

    /// Replaces the passphrase once the current one is proved. Not for a gate whose passphrase belongs
    /// to something else. A gate that keeps a vault re-seals it first (see the ui's PassphraseChange).
    public synchronized boolean change(String current, String newPassphrase) {
        if (external != null || !isValidPassphrase(newPassphrase) || !verify(current)) return false;
        store(newPassphrase);
        return true;
    }

    public boolean canChange() { return external == null && hasPassphrase(); }

    /// A reminder the human wrote for themself, shown on the unlock window: where the passphrase is
    /// kept, say, never the passphrase. Plain text in the passphrase file; empty for none.
    public String hint() { return hint; }

    public synchronized void setHint(String text) {
        if (external != null || !hasPassphrase()) return;
        hint = text == null ? "" : text.strip();
        write();
    }

    public synchronized boolean unlock(String passphrase) {
        if (!verify(passphrase)) return false;
        if (external == null && !"pbkdf2".equals(kind)) store(passphrase);
        unlocked = true;
        return true;
    }

    public synchronized boolean verify(String passphrase) {
        if (!hasPassphrase() || passphrase == null) return false;
        if (external != null) return external.test(passphrase);
        var salt = HexFormat.of().parseHex(saltHex);
        var candidate = "pbkdf2".equals(kind) ? pbkdf2(salt, passphrase) : sha256(salt, passphrase);
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.US_ASCII), hashHex.getBytes(StandardCharsets.US_ASCII));
    }

    // -- Tier 2: blanket mode --

    public boolean isBlanketModeActive() {
        var expires = blanketModeExpiresAt;
        return expires != null && Instant.now().isBefore(expires);
    }

    public Duration blanketModeRemaining() {
        var expires = blanketModeExpiresAt;
        if (expires == null) return Duration.ZERO;
        var remaining = Duration.between(Instant.now(), expires);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /// Activates blanket mode for `minutes`, clamped to 1..`BLANKET_MODE_MAX_MINUTES`. The caller
    /// (the tray menu action) has already confirmed the passphrase TWICE.
    public void activateBlanketMode(int minutes) {
        blanketModeExpiresAt = Instant.now().plusSeconds(Math.clamp(minutes, 1, BLANKET_MODE_MAX_MINUTES) * 60L);
    }

    /// Adds `minutes` to what is left of blanket mode (or starts it from now), never leaving more than
    /// the cap. Returns the new end. The caller has already checked the passphrase.
    public Instant extendBlanketMode(int minutes) {
        var now = Instant.now();
        var current = blanketModeExpiresAt;
        var from = current != null && current.isAfter(now) ? current : now;
        var added = from.plusSeconds(Math.clamp(minutes, 1, BLANKET_MODE_MAX_MINUTES) * 60L);
        var ceiling = now.plusSeconds(BLANKET_MODE_MAX_MINUTES * 60L);
        blanketModeExpiresAt = added.isAfter(ceiling) ? ceiling : added;
        return blanketModeExpiresAt;
    }

    public void deactivateBlanketModeNow() { blanketModeExpiresAt = null; }

    // -- persistence --

    /// The PBKDF2 hash lives beside the original file, never over it, so an older build of the
    /// same gate still opens with its own file if someone goes back to it.
    Path upgradedPath() {
        var name = storePath.getFileName().toString().replaceFirst("\\.json$", "");
        return storePath.resolveSibling(name + ".pbkdf2.json");
    }

    void load() {
        var file = Files.exists(upgradedPath()) ? upgradedPath() : storePath;
        if (!Files.exists(file)) return;
        try {
            var root = mapper.readTree(Files.readString(file));
            saltHex = root.get("saltHex").asText();
            hashHex = root.get("hashHex").asText();
            kind = root.path("kind").asText("sha256");
            hint = root.path("hint").asText("");
        } catch (IOException e) {
            throw new RuntimeException("cannot read " + storePath, e);
        }
    }

    void store(String passphrase) {
        var salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        saltHex = HexFormat.of().formatHex(salt);
        hashHex = pbkdf2(salt, passphrase);
        kind = "pbkdf2";
        write();
    }

    void write() {
        try {
            Files.createDirectories(storePath.getParent());
            var node = mapper.createObjectNode().put("kind", kind).put("iterations", ITERATIONS)
                .put("saltHex", saltHex).put("hashHex", hashHex).put("createdAt", Instant.now().toEpochMilli());
            if (!hint.isBlank()) node.put("hint", hint);
            var tmp = upgradedPath().resolveSibling(upgradedPath().getFileName() + ".tmp");
            Files.writeString(tmp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
            Files.move(tmp, upgradedPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("cannot write " + upgradedPath(), e);
        }
    }

    static String pbkdf2(byte[] salt, String passphrase) {
        try {
            var spec = new PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256);
            return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static String sha256(byte[] salt, String passphrase) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(passphrase.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
