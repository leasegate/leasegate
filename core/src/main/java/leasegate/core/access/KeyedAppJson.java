package leasegate.core.access;

import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/// The on-disk shape of `keyed-apps.json`: a plain array, instants as epoch millis, the scope's own
/// fields inline in each entry (written and read by the gate's LeaseRules).
final class KeyedAppJson {

    static <S extends Scope<S>> List<KeyedApp<S>> load(ObjectMapper m, Path file, LeaseRules<S> rules) {
        if (!Files.exists(file)) return List.of();
        try {
            var out = new ArrayList<KeyedApp<S>>();
            for (var n : m.readTree(Files.readString(file))) {
                out.add(new KeyedApp<>(n.get("id").asText(), n.get("app").asText(), n.get("publicKey").asText(),
                    n.get("fingerprint").asText(), rules.readScope(n),
                    Instant.ofEpochMilli(n.get("approvedAt").asLong()), Instant.ofEpochMilli(n.get("expiresAt").asLong()),
                    n.hasNonNull("lastSeenImagePath") ? n.get("lastSeenImagePath").asText() : null,
                    n.hasNonNull("lastSeenPid") ? n.get("lastSeenPid").asLong() : null, n.path("flagged").asBoolean(false)));
            }
            return out;
        } catch (Exception e) { throw new IllegalStateException("cannot read " + file, e); }
    }

    static <S extends Scope<S>> void save(ObjectMapper m, Path file, List<KeyedApp<S>> apps, LeaseRules<S> rules) {
        try {
            Files.createDirectories(file.getParent());
            var arr = m.createArrayNode();
            for (var a : apps) arr.add(node(m, a, rules));
            Files.writeString(file, m.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
        } catch (Exception e) { throw new IllegalStateException("cannot write " + file, e); }
    }

    static <S extends Scope<S>> ObjectNode node(ObjectMapper m, KeyedApp<S> a, LeaseRules<S> rules) {
        var n = m.createObjectNode().put("id", a.id()).put("app", a.app()).put("publicKey", a.publicKey())
            .put("fingerprint", a.fingerprint()).put("approvedAt", a.approvedAt().toEpochMilli())
            .put("expiresAt", a.expiresAt().toEpochMilli()).put("flagged", a.flagged());
        if (a.lastSeenImagePath() != null) n.put("lastSeenImagePath", a.lastSeenImagePath());
        if (a.lastSeenPid() != null) n.put("lastSeenPid", a.lastSeenPid());
        rules.writeScope(n, a.scope());
        return n;
    }

    private KeyedAppJson() {}
}
