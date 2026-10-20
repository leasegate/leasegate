package leasegate.core.trial;

import java.nio.file.Files;
import java.nio.file.Path;

import leasegate.core.access.PassphraseGate;

/// Pure logic proof for PassphraseGate -- no JavaFX, no pipe. Covers Tier 0 (set-once, unlock,
/// wrong passphrase, no reset) and Tier 2 (blanket mode activation/deactivation/hard cap), plus
/// restart survival (a fresh instance reloading the same store file still verifies the same
/// passphrase).
/// java -cp <classes> leasegate.core.trial.PassphraseGateTrial
public class PassphraseGateTrial {
    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("leasegate-passphrase-trial");
        var storePath = dir.resolve("passphrase.json");

        var gate = new PassphraseGate(storePath);
        check("no passphrase set yet", !gate.hasPassphrase());
        check("not unlocked before anything is set", !gate.isUnlocked());

        check("rejects non-alphanumeric on setup", !gate.setInitialPassphrase("has a space"));
        check("rejects blank on setup", !gate.setInitialPassphrase(""));
        check("accepts a valid alphanumeric passphrase", gate.setInitialPassphrase("Sunrise42"));
        check("setInitialPassphrase unlocks in the same act", gate.isUnlocked());
        check("cannot set a second passphrase once one exists", !gate.setInitialPassphrase("Другое1"));

        var fresh = new PassphraseGate(storePath);
        check("a fresh instance is locked even though a passphrase already exists on disk", !fresh.isUnlocked());
        check("wrong passphrase does not unlock", !fresh.unlock("wrong"));
        check("still locked after a wrong attempt", !fresh.isUnlocked());
        check("right passphrase unlocks (restart survival)", fresh.unlock("Sunrise42"));
        check("now unlocked", fresh.isUnlocked());
        check("verify() does not itself unlock (Tier 1/2 use it read-only)",
            new PassphraseGate(storePath).verify("Sunrise42") && !new PassphraseGate(storePath).isUnlocked());

        // Tier 2: blanket mode.
        check("blanket mode starts inactive", !fresh.isBlanketModeActive());
        check("blanket mode remaining is zero when inactive", fresh.blanketModeRemaining().isZero());
        fresh.activateBlanketMode(PassphraseGate.BLANKET_MODE_MAX_MINUTES);
        check("blanket mode active right after activation", fresh.isBlanketModeActive());
        check("blanket mode remaining is at most the hard cap",
            fresh.blanketModeRemaining().toMinutes() <= PassphraseGate.BLANKET_MODE_MAX_MINUTES);
        check("blanket mode remaining is close to the hard cap right after activation",
            fresh.blanketModeRemaining().toMinutes() >= PassphraseGate.BLANKET_MODE_MAX_MINUTES - 1);
        fresh.deactivateBlanketModeNow();
        check("deactivateBlanketModeNow turns it off immediately", !fresh.isBlanketModeActive());
        check("the hard cap is four hours", PassphraseGate.BLANKET_MODE_MAX_MINUTES == 240);
        fresh.activateBlanketMode(30);
        check("a chosen 30 minutes runs about 30 minutes",
            fresh.blanketModeRemaining().toMinutes() >= 29 && fresh.blanketModeRemaining().toMinutes() < 30 + 1);
        fresh.activateBlanketMode(10_000);
        check("a duration above the cap is clamped to it",
            fresh.blanketModeRemaining().toMinutes() <= PassphraseGate.BLANKET_MODE_MAX_MINUTES);
        fresh.deactivateBlanketModeNow();

        // A file written by an earlier version: one salted SHA-256. It still opens, and the first
        // unlock writes a PBKDF2 copy beside it without touching the original.
        var legacy = dir.resolve("legacy.json");
        var salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        var md = java.security.MessageDigest.getInstance("SHA-256");
        md.update(salt);
        md.update("Oldpass7".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var hex = java.util.HexFormat.of();
        var legacyText = "{\"saltHex\":\"" + hex.formatHex(salt) + "\",\"hashHex\":\"" + hex.formatHex(md.digest()) + "\"}";
        Files.writeString(legacy, legacyText);
        var old = new PassphraseGate(legacy);
        check("a legacy file verifies its passphrase", old.verify("Oldpass7") && !old.verify("wrong"));
        check("unlocking a legacy file works", old.unlock("Oldpass7"));
        var upgraded = dir.resolve("legacy.pbkdf2.json");
        check("the upgrade is written beside the original", Files.exists(upgraded) && Files.readString(upgraded).contains("pbkdf2"));
        check("the original file is left exactly as it was", Files.readString(legacy).equals(legacyText));
        var reread = new PassphraseGate(legacy);
        check("a fresh instance reads the upgraded file and still verifies", reread.verify("Oldpass7") && !reread.verify("wrong"));
        check("a new passphrase after recovery replaces it", reread.resetAfterRecovery("Newpass8") && new PassphraseGate(legacy).verify("Newpass8"));

        try (var files = Files.list(dir)) { for (var f : files.toList()) Files.deleteIfExists(f); }
        Files.deleteIfExists(dir);

        System.out.println("ALL CHECKS PASSED");
    }

    static void check(String label, boolean condition) {
        if (!condition) throw new AssertionError("FAILED: " + label);
        System.out.println("ok - " + label);
    }
}
