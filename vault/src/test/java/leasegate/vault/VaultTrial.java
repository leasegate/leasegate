package leasegate.vault;

import java.nio.file.Files;

/// Pure logic proof for the vault: both slots open it, wrong keys do not, a changed passphrase
/// replaces the old one, and the file never holds a secret's name or value in the clear.
/// java -cp <classes> leasegate.vault.VaultTrial
public class VaultTrial {

    static int failed;

    public static void main(String[] args) throws Exception {
        var file = Files.createTempDirectory("vault-trial").resolve("vault.json");
        var created = Vault.create(file, "Sunrise42");
        var recovery = created.recoveryKey();
        System.out.println("  (recovery key looks like: " + recovery.replaceAll("[A-Z2-7]", "X") + ")");
        created.vault().put("token.example", "tok_SECRET_VALUE_123");
        created.vault().close();
        var text = Files.readString(file);
        check("the file holds neither the name nor the value", !text.contains("SECRET_VALUE") && !text.contains("token.example"));

        try (var v = Vault.open(file, "Sunrise42")) { check("the passphrase opens it and the secret is back", v.get("token.example").orElse("").equals("tok_SECRET_VALUE_123")); }
        check("a wrong passphrase does not open it", throwsWrongKey(() -> Vault.open(file, "sunrise42")));
        try (var v = Vault.openWithRecovery(file, recovery.toLowerCase().replace("-", " "))) {
            check("the recovery key opens it, typed in lower case with spaces", v.names().contains("token.example"));
            v.changePassphrase("Moonrise7");
        }
        check("after the change the old passphrase fails", throwsWrongKey(() -> Vault.open(file, "Sunrise42")));
        try (var v = Vault.open(file, "Moonrise7")) { check("and the new one opens it", v.get("token.example").isPresent()); }
        try (var v = Vault.openWithRecovery(file, recovery)) { check("the recovery key still works after a passphrase change", v.get("token.example").isPresent()); }
        var wrongRecovery = RecoveryKey.format(Crypto.random(32));
        check("another recovery key does not open it", throwsWrongKey(() -> Vault.openWithRecovery(file, wrongRecovery)));
        var raw = Crypto.random(32);
        check("a recovery key reads back to the same bytes", java.util.Arrays.equals(raw, RecoveryKey.parse(RecoveryKey.format(raw))));
        check("creating over an existing vault is refused", throwsAny(() -> Vault.create(file, "x")));

        System.out.println(failed == 0 ? "ALL CHECKS PASSED" : failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }

    static boolean throwsWrongKey(Runnable r) {
        try { r.run(); return false; } catch (WrongKeyException e) { return true; }
    }

    static boolean throwsAny(Runnable r) {
        try { r.run(); return false; } catch (RuntimeException e) { return true; }
    }

    static void check(String label, boolean ok) {
        if (!ok) failed++;
        System.out.println((ok ? "ok - " : "FAILED - ") + label);
    }
}
