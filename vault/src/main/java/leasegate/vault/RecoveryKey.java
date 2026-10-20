package leasegate.vault;

import java.util.Locale;

/// 256 random bits written for a human: base32 (A-Z, 2-7), in groups of four, so it can be copied
/// onto paper without ambiguity and typed back with or without the dashes, in either case.
public final class RecoveryKey {

    static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public static String format(byte[] key) {
        var sb = new StringBuilder();
        var buffer = 0;
        var bits = 0;
        for (var b : key) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) { sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31)); bits -= 5; }
        }
        if (bits > 0) sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        var grouped = new StringBuilder();
        for (var i = 0; i < sb.length(); i++) {
            if (i > 0 && i % 4 == 0) grouped.append('-');
            grouped.append(sb.charAt(i));
        }
        return grouped.toString();
    }

    /// Throws IllegalArgumentException on a character that is not in the alphabet.
    public static byte[] parse(String text) {
        var clean = text.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        var out = new byte[clean.length() * 5 / 8];
        var buffer = 0;
        var bits = 0;
        var n = 0;
        for (var ch : clean.toCharArray()) {
            var v = ALPHABET.indexOf(ch);
            if (v < 0) throw new IllegalArgumentException("not a recovery key character: " + ch);
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8 && n < out.length) { out[n++] = (byte) (buffer >> (bits - 8)); bits -= 8; }
        }
        return out;
    }

    private RecoveryKey() {}
}
