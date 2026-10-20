package leasegate.sshagent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/// Reads the SSH wire encoding the agent protocol is made of (RFC 4251 section 5): big-endian
/// uint32, single bytes, and "strings" that are a uint32 length followed by that many bytes.
final class SshReader {

    final ByteBuffer b;

    SshReader(byte[] bytes) { b = ByteBuffer.wrap(bytes); }

    int u8() { return b.get() & 0xff; }

    int u32() { return b.getInt(); }

    byte[] string() {
        var n = b.getInt();
        if (n < 0 || n > b.remaining()) throw new IllegalArgumentException("bad string length " + n);
        var out = new byte[n];
        b.get(out);
        return out;
    }

    String text() { return new String(string(), StandardCharsets.UTF_8); }
}
