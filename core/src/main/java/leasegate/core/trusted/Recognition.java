package leasegate.core.trusted;

import java.time.Instant;

/// What the gate found out about a calling process, once per process start. `Recognized` names the
/// exact code it runs; `Refused` says why no long approval can be bound to it.
public sealed interface Recognition {

    long pid();

    /// `jarSha256Pinned`: the exe carries the jar's sha256 and the local jar matched it, so the exe's
    /// hash already fixes the jar. `icon` is the exe's own PNG icon, or null.
    record Recognized(long pid, Instant start, String exe, String exeSha256, String jar, String jarSha256,
                      boolean jarSha256Pinned, byte[] icon) implements Recognition {

        public boolean sameCode(String exeHash, String jarHash) {
            return exeSha256.equals(exeHash) && java.util.Objects.equals(jarSha256, jarHash);
        }
    }

    record Refused(long pid, String reason, String hint) implements Recognition {}
}
