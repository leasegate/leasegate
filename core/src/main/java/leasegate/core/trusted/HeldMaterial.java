package leasegate.core.trusted;

import java.util.*;

/// The secret material one long approval needs, kept in memory only while that approval is open, so
/// it keeps working after the host locks. Nothing else stays unsealed. Zero-filled the moment the
/// approval expires or is ended; a host restart loses it, which ends every long approval.
public final class HeldMaterial implements AutoCloseable {

    public static final HeldMaterial NONE = new HeldMaterial(Map.of());

    final Map<String, byte[]> items;
    volatile boolean closed;

    public HeldMaterial(Map<String, byte[]> items) { this.items = new HashMap<>(items); }

    public synchronized Optional<byte[]> get(String name) {
        if (closed) return Optional.empty();
        var v = items.get(name);
        return v == null ? Optional.empty() : Optional.of(v.clone());
    }

    public synchronized Set<String> names() { return closed ? Set.of() : Set.copyOf(items.keySet()); }

    public boolean isClosed() { return closed; }

    @Override public synchronized void close() {
        if (this == NONE) return;
        items.values().forEach(v -> Arrays.fill(v, (byte) 0));
        items.clear();
        closed = true;
    }
}
