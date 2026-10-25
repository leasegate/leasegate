package leasegate.core.trusted;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import leasegate.core.access.Scope;

/// The open long approvals, in memory only. They run on their own clocks, untouched by the host's
/// idle lock; each ends when it expires, when the human ends it, or when the host quits. Ending one
/// zero-fills its material at once. How each run of an app last had its approval end is kept, so its
/// status can say "expired" or "ended" rather than just "not open".
public final class LongApprovals<S extends Scope<S>> {

    public enum Ended { EXPIRED, ENDED }

    final Map<String, LongApproval<S>> open = new ConcurrentHashMap<>();
    final Map<String, Ended> lastEnd = new ConcurrentHashMap<>();
    final Consumer<String> audit;

    public LongApprovals(Consumer<String> audit) { this.audit = audit; }

    public LongApprovals<S> startSweep() {
        Thread.ofPlatform().name("trusted-apps-sweep").daemon().start(() -> {
            while (true) {
                try { Thread.sleep(5_000); } catch (InterruptedException e) { return; }
                sweep(Instant.now());
            }
        });
        return this;
    }

    public LongApproval<S> add(LongApproval<S> a) {
        open.values().stream().filter(x -> x.appId().equals(a.appId())).toList().forEach(x -> end(x, Ended.ENDED, "replaced by a new approval"));
        open.put(a.id(), a);
        lastEnd.remove(a.pid() + "@" + a.start());
        audit.accept("long approval opened for " + a.appName() + " (pid " + a.pid() + ") until " + a.expiresAt() + " [" + a.id() + "]");
        return a;
    }

    public List<LongApproval<S>> all() {
        return open.values().stream().sorted(Comparator.comparing(LongApproval::issuedAt)).toList();
    }

    /// The live approval of this run of a process, if any.
    public Optional<LongApproval<S>> forProcess(long pid, Instant start, Instant now) {
        return open.values().stream().filter(a -> a.forProcess(pid, start) && !a.expired(now)).findFirst();
    }

    public Optional<Ended> lastEnd(long pid, Instant start) { return Optional.ofNullable(lastEnd.get(pid + "@" + start)); }

    public void end(String id, String why) { var a = open.get(id); if (a != null) end(a, Ended.ENDED, why); }

    public int endAll(String why) {
        var all = List.copyOf(open.values());
        all.forEach(a -> end(a, Ended.ENDED, why));
        return all.size();
    }

    void sweep(Instant now) {
        open.values().stream().filter(a -> a.expired(now) || !alive(a)).toList()
            .forEach(a -> end(a, a.expired(now) ? Ended.EXPIRED : Ended.ENDED, a.expired(now) ? "expired" : "the app exited"));
    }

    static boolean alive(LongApproval<?> a) {
        return ProcessHandle.of(a.pid()).filter(ProcessHandle::isAlive).flatMap(p -> p.info().startInstant()).map(a.start()::equals).orElse(false);
    }

    void end(LongApproval<S> a, Ended how, String why) {
        if (open.remove(a.id()) == null) return;
        a.material().close();
        lastEnd.put(a.pid() + "@" + a.start(), how);
        audit.accept("long approval for " + a.appName() + " (pid " + a.pid() + ") ended: " + why + " [" + a.id() + "]");
    }
}
