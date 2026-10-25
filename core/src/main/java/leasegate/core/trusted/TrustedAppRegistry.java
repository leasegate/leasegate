package leasegate.core.trusted;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/// The gate's `trusted-apps.json`: add, list, show, remove. Holds names, paths and hashes only, so
/// the file itself grants nothing. Every change is told to `audit`, for the host's own log.
public final class TrustedAppRegistry {

    static final ObjectMapper M = new ObjectMapper();

    final Path file;
    final Consumer<String> audit;
    final List<TrustedApp> apps = new ArrayList<>();

    public TrustedAppRegistry(Path file, Consumer<String> audit) {
        this.file = file;
        this.audit = audit;
        load();
    }

    public synchronized List<TrustedApp> all() { return List.copyOf(apps); }

    public synchronized Optional<TrustedApp> show(String id) { return apps.stream().filter(a -> a.id().equals(id)).findFirst(); }

    /// The app registered for this exe path, whatever code it runs now.
    public synchronized Optional<TrustedApp> byExe(String exe) {
        return apps.stream().filter(a -> Recognizer.same(a.exe(), exe)).findFirst();
    }

    /// Adds the app, or replaces the entry for the same exe (a re-trust after its code changed).
    public synchronized TrustedApp put(TrustedApp app) {
        var previous = byExe(app.exe());
        var entry = previous.map(p -> new TrustedApp(p.id(), app.name(), app.exe(), app.exeSha256(), app.jar(), app.jarSha256(),
            app.project(), app.assets(), app.level(), app.release(), app.verifiedAt(), app.maxMinutes(), p.trustedAt(), app.lastApprovedAt()))
            .orElse(app);
        apps.removeIf(a -> a.id().equals(entry.id()));
        apps.add(entry);
        save();
        var changed = previous.filter(p -> !p.exeSha256().equals(entry.exeSha256()) || !Objects.equals(p.jarSha256(), entry.jarSha256()));
        audit.accept((previous.isEmpty() ? "trusted app added: " : changed.isPresent() ? "trusted app re-trusted with new code: " : "trusted app updated: ")
            + describe(entry) + changed.map(p -> " (was exe " + Hashes.brief(p.exeSha256()) + ", jar " + Hashes.brief(p.jarSha256()) + ")").orElse(""));
        return entry;
    }

    public synchronized boolean remove(String id) {
        var gone = show(id);
        if (gone.isEmpty()) return false;
        apps.removeIf(a -> a.id().equals(id));
        save();
        audit.accept("trusted app removed: " + describe(gone.get()));
        return true;
    }

    public static String describe(TrustedApp a) {
        return a.name() + " [" + a.id() + "] " + a.exe() + " exe " + Hashes.brief(a.exeSha256())
            + (a.jar() == null ? "" : ", jar " + a.jar() + " " + Hashes.brief(a.jarSha256())) + ", " + a.level()
            + (a.releaseLabel().isEmpty() ? "" : " " + a.releaseLabel());
    }

    void load() {
        if (!Files.exists(file)) return;
        try { for (var n : M.readTree(Files.readString(file))) apps.add(read(n)); }
        catch (Exception e) { throw new IllegalStateException("cannot read " + file, e); }
    }

    void save() {
        try {
            Files.createDirectories(file.getParent());
            var arr = M.createArrayNode();
            apps.forEach(a -> arr.add(write(a)));
            var tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, M.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) { throw new IllegalStateException("cannot write " + file, e); }
    }

    static ObjectNode write(TrustedApp a) {
        var n = M.createObjectNode().put("id", a.id()).put("name", a.name()).put("exe", a.exe()).put("exeSha256", a.exeSha256())
            .put("jar", a.jar()).put("jarSha256", a.jarSha256()).put("project", a.project()).put("level", a.level().name())
            .put("release", a.release()).put("maxMinutes", a.maxMinutes()).put("trustedAt", a.trustedAt().toString());
        if (a.verifiedAt() != null) n.put("verifiedAt", a.verifiedAt().toString());
        if (a.lastApprovedAt() != null) n.put("lastApprovedAt", a.lastApprovedAt().toString());
        var assets = n.putArray("assets");
        a.assets().forEach(x -> assets.addObject().put("kind", x.kind()).put("url", x.url()));
        return n;
    }

    static TrustedApp read(JsonNode n) {
        var assets = new ArrayList<Declaration.Asset>();
        n.path("assets").forEach(x -> assets.add(new Declaration.Asset(x.path("kind").asText(), x.path("url").asText())));
        return new TrustedApp(n.get("id").asText(), n.get("name").asText(), n.get("exe").asText(), n.get("exeSha256").asText(),
            n.path("jar").asText(null), n.path("jarSha256").asText(null), n.path("project").asText(null), List.copyOf(assets),
            TrustedApp.Level.valueOf(n.path("level").asText("DEV")), n.path("release").asText(null), instant(n, "verifiedAt"),
            n.path("maxMinutes").asInt(TrustedApp.DEFAULT_MINUTES), instant(n, "trustedAt"), instant(n, "lastApprovedAt"));
    }

    static Instant instant(JsonNode n, String f) { return n.hasNonNull(f) ? Instant.parse(n.get(f).asText()) : null; }
}
