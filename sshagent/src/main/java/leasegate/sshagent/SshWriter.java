package leasegate.sshagent;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/// Writes the SSH wire encoding (see SshReader).
final class SshWriter {

    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    SshWriter u8(int v) { out.write(v); return this; }

    SshWriter u32(int v) { out.write(v >>> 24); out.write(v >>> 16); out.write(v >>> 8); out.write(v); return this; }

    SshWriter string(byte[] s) { u32(s.length); out.writeBytes(s); return this; }

    SshWriter string(String s) { return string(s.getBytes(StandardCharsets.UTF_8)); }

    byte[] bytes() { return out.toByteArray(); }

    /// The bytes so far, prefixed by their own length: one agent message.
    byte[] framed() { return new SshWriter().string(bytes()).bytes(); }
}
