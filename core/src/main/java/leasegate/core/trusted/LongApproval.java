package leasegate.core.trusted;

import java.time.Instant;

import leasegate.core.access.Scope;

/// An open long approval: one run of one trusted app (pid and start time), the exact code it was
/// opened for, its scope, and until when. The host checks each request against `scope` and takes
/// what it needs from `material`.
public record LongApproval<S extends Scope<S>>(String id, String appId, String appName, long pid, Instant start,
                                               String exeSha256, String jarSha256, S scope, Instant issuedAt,
                                               Instant expiresAt, HeldMaterial material) {

    public boolean expired(Instant now) { return !now.isBefore(expiresAt); }

    public boolean forProcess(long pid, Instant start) { return this.pid == pid && this.start.equals(start); }
}
