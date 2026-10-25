package leasegate.core.trusted;

import java.time.Instant;
import java.util.List;

/// One trusted app as the registry keeps it: what it is, the exact code that was trusted, how far
/// that code was verified, and the most it may be granted. No secret is ever in here.
/// `level` is DEV (hashes checked locally) or VERIFIED (matched a GitHub release, `release` names it).
public record TrustedApp(String id, String name, String exe, String exeSha256, String jar, String jarSha256,
                         String project, List<Declaration.Asset> assets, Level level, String release, Instant verifiedAt,
                         int maxMinutes, Instant trustedAt, Instant lastApprovedAt) {

    public enum Level { DEV, VERIFIED }

    public static final int DEFAULT_MINUTES = 480, MAX_MINUTES = 1440;

    public boolean runs(Recognition.Recognized r) { return r.sameCode(exeSha256, jarSha256); }

    /// "org/repo tag" for a verified app, empty otherwise.
    public String releaseLabel() { return level == Level.VERIFIED && release != null ? release : ""; }
}
