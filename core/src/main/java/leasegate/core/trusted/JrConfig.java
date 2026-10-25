package leasegate.core.trusted;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/// A jr exe's embedded config (RCDATA "JRC", jrc-json), and the jar it runs, found the way jr finds
/// it: the first `path` source that exists, else a `maven` one in ~/.m2, else a `url` one in jr's
/// own download cache. `sha256` is the hash baked into the exe for downloaded jars, if any.
public record JrConfig(List<JsonNode> sources, String sha256, boolean javaArgs, String jvmMode) {

    static final ObjectMapper M = new ObjectMapper();

    public static Optional<JrConfig> parse(byte[] raw) {
        try {
            var text = new String(raw, StandardCharsets.UTF_8).replaceFirst("^﻿", "").strip();
            if (!text.startsWith("{")) return Optional.empty();
            var n = M.readTree(text);
            var sources = new ArrayList<JsonNode>();
            n.path("jar").path("sources").forEach(sources::add);
            return Optional.of(new JrConfig(sources, n.path("jar").path("sha256").asText(null),
                n.path("jvm").hasNonNull("javaArgs"), n.path("jvm").path("mode").asText("")));
        } catch (Exception e) { return Optional.empty(); }
    }

    public Optional<Path> jar(Path exeDir) {
        for (var s : sources) if (s.hasNonNull("path")) {
            var p = exeDir.resolve(expand(s.get("path").asText()));
            if (Files.isRegularFile(p)) return Optional.of(p.normalize());
        }
        var home = Path.of(System.getProperty("user.home"));
        for (var s : sources) {
            if (s.hasNonNull("maven")) {
                var p = maven(home, s.get("maven").asText());
                if (p.isPresent() && Files.isRegularFile(p.get())) return p;
            }
            if (s.hasNonNull("url") && sha256 != null) {
                var p = cached(home.resolve(".jr").resolve("cache").resolve("jars").resolve(sha256.toLowerCase()));
                if (p.isPresent()) return p;
            }
        }
        return Optional.empty();
    }

    static Optional<Path> maven(Path home, String coordinate) {
        var c = coordinate.split(":");
        if (c.length < 3 || c.length > 4) return Optional.empty();
        var file = c[1] + "-" + c[2] + (c.length == 4 ? "-" + c[3] : "") + ".jar";
        var p = home.resolve(".m2").resolve("repository");
        for (var part : c[0].split("\\.")) p = p.resolve(part);
        return Optional.of(p.resolve(c[1]).resolve(c[2]).resolve(file));
    }

    static Optional<Path> cached(Path dir) {
        try (var files = Files.list(dir)) { return files.filter(f -> f.toString().endsWith(".jar")).findFirst(); }
        catch (Exception e) { return Optional.empty(); }
    }

    /// `%VAR%` as jr expands it; an unknown variable is left as it is.
    static String expand(String s) {
        var m = java.util.regex.Pattern.compile("%([^%]+)%").matcher(s);
        var out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(Objects.requireNonNullElse(System.getenv(m.group(1)), m.group())));
        return m.appendTail(out).toString();
    }
}
