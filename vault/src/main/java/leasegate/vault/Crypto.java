package leasegate.vault;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/// The few primitives the vault needs, all from the JDK: AES-256-GCM, PBKDF2-HMAC-SHA256, and a
/// SHA-256 to turn a recovery key (already 256 random bits) into a key. A wrong key is told apart
/// from a damaged file only by the GCM tag failing, which is all either case deserves.
final class Crypto {

    static final SecureRandom RANDOM = new SecureRandom();
    static final int KEY_BYTES = 32, IV_BYTES = 12, TAG_BITS = 128;

    static byte[] random(int n) {
        var b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    static byte[] pbkdf2(String passphrase, byte[] salt, int iterations) {
        try {
            var spec = new PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BYTES * 8);
            try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
            finally { spec.clearPassword(); }
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    static byte[] fromRecovery(byte[] recovery) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update("leasegate-vault-recovery".getBytes(StandardCharsets.US_ASCII));
            return md.digest(recovery);
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    /// Returns iv followed by ciphertext and tag. `aad` binds the blob to its place in the file.
    static byte[] seal(byte[] key, byte[] plain, String aad) {
        try {
            var iv = random(IV_BYTES);
            var c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(aad.getBytes(StandardCharsets.US_ASCII));
            var ct = c.doFinal(plain);
            var out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return out;
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    /// Throws WrongKeyException when the key (or the file) is not the one that sealed it.
    static byte[] open(byte[] key, byte[] sealed, String aad) {
        try {
            var c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES));
            c.updateAAD(aad.getBytes(StandardCharsets.US_ASCII));
            return c.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new WrongKeyException();
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    private Crypto() {}
}
