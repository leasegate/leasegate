package leasegate.core.trusted;

import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/// SHA-256 as lower-case hex, of bytes, of a file, of a stream (with a size limit), and the short
/// form shown to humans.
public final class Hashes {

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static String sha256(Path file) {
        try (var in = Files.newInputStream(file)) { return sha256(in, Long.MAX_VALUE); }
        catch (Exception e) { throw new IllegalStateException("cannot hash " + file, e); }
    }

    /// Throws when more than `limit` bytes arrive.
    public static String sha256(InputStream in, long limit) throws java.io.IOException {
        try {
            var d = MessageDigest.getInstance("SHA-256");
            var buf = new byte[1 << 16];
            var total = 0L;
            for (int n; (n = in.read(buf)) > 0; ) {
                if ((total += n) > limit) throw new java.io.IOException("larger than " + (limit >> 20) + " MB");
                d.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(d.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static String brief(String hex) { return hex == null ? "-" : hex.substring(0, Math.min(16, hex.length())); }

    private Hashes() {}
}
