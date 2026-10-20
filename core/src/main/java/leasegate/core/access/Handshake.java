package leasegate.core.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/// The first frame of a connection: `{"gate":{"v":2,"op":..,"app":..,"minutes":N,"publicKey":..,"pid":N,..}}`.
/// `op` is the only field every request carries; the fields that describe the scope are the gate's
/// own and are read from `body` by its LeaseRules. An absent `minutes` means "not specified" and is
/// shown to the human as exactly that.
public record Handshake(String op, String app, Integer minutes, String publicKey, Long pid, JsonNode body) {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static Optional<Handshake> extract(String frame) {
        JsonNode g;
        try { g = MAPPER.readTree(frame).path("gate"); } catch (Exception e) { return Optional.empty(); }
        if (!g.isObject() || !g.has("op")) return Optional.empty();
        return Optional.of(new Handshake(g.get("op").asText(), g.path("app").asText(null),
            g.hasNonNull("minutes") ? g.get("minutes").asInt() : null, g.path("publicKey").asText(null),
            g.hasNonNull("pid") ? g.get("pid").asLong() : null, g));
    }

    public boolean keyed() { return publicKey != null && !publicKey.isBlank(); }

    /// A list of non-blank strings, or null when the field is absent ("not narrowed").
    public static List<String> strings(JsonNode obj, String field) {
        if (!obj.hasNonNull(field) || !obj.get(field).isArray()) return null;
        var out = new ArrayList<String>();
        for (var e : obj.get(field)) if (!e.asText().isBlank()) out.add(e.asText().trim());
        return out;
    }

    /// The signature a keyed client sends back in answer to a challenge.
    public static String signatureOf(String frame) {
        if (frame == null) return "";
        try { return MAPPER.readTree(frame).path("gate").path("signature").asText(""); }
        catch (Exception e) { return ""; }
    }
}
