package leasegate.core.trusted;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/// What an app says about itself (the request's `app` object). Every part is checked and none of it
/// is believed: the gate finds the exe and jar itself, hashes them itself, and downloads the assets
/// itself. A missing `exe` or `jar` just means the app left that check to the gate.
public record Declaration(String name, String exe, String jar, String project, List<Asset> assets) {

    public record Asset(String kind, String url) {}

    public static Declaration of(JsonNode app) {
        var assets = new ArrayList<Asset>();
        app.path("assets").forEach(a -> assets.add(new Asset(a.path("kind").asText(""), a.path("url").asText(""))));
        var name = app.path("name").asText("").strip();
        return new Declaration(name.isEmpty() ? "(unnamed app)" : name, text(app, "exe"), text(app, "jar"), text(app, "project"), List.copyOf(assets));
    }

    static String text(JsonNode n, String field) {
        var s = n.path(field).asText("").strip();
        return s.isEmpty() ? null : s;
    }
}
