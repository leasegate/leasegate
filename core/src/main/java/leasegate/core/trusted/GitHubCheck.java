package leasegate.core.trusted;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

import leasegate.core.trusted.Recognition.Recognized;

/// The green tick: every release asset the app declares is downloaded from GitHub over HTTPS by the
/// gate itself, hashed as it streams in (nothing is written to disk), and must equal the local file
/// of its kind. The assets must be release downloads of the declared project, so the tick can only
/// ever name the repository the files came from. What that organisation publishes is its own
/// responsibility; this is the gate's due diligence, and the human still decides.
public final class GitHubCheck {

    public sealed interface Result {}
    public record Verified(String org, String repo, String tag, Instant at) implements Result {}
    public record NotVerified(String reason) implements Result {}

    static final Pattern PROJECT = Pattern.compile("https://github\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9._-]+?)(?:\\.git)?/?");
    static final Pattern ASSET = Pattern.compile("https://github\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9._-]+)/releases/download/([^/]+)/([^/?#]+)");
    static final long LIMIT = 1L << 30;

    final HttpClient http;

    public GitHubCheck() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build());
    }

    GitHubCheck(HttpClient http) { this.http = http; }

    public Result check(Declaration d, Recognized r) {
        if (d.project() == null || d.assets().isEmpty()) return new NotVerified("no GitHub project and release assets declared");
        var p = PROJECT.matcher(d.project());
        if (!p.matches()) return new NotVerified("the project is not a https://github.com/<org>/<repo> address: " + d.project());
        String tag = null;
        boolean exeOk = false, jarOk = false;
        for (var a : d.assets()) {
            var m = ASSET.matcher(a.url());
            if (!m.matches() || !m.group(1).equalsIgnoreCase(p.group(1)) || !m.group(2).equalsIgnoreCase(p.group(2)))
                return new NotVerified("not a release download of " + p.group(1) + "/" + p.group(2) + ": " + a.url());
            if (tag != null && !tag.equals(m.group(3))) return new NotVerified("the assets come from two releases, " + tag + " and " + m.group(3));
            tag = m.group(3);
            var local = switch (a.kind()) { case "exe" -> r.exeSha256(); case "jar" -> r.jarSha256(); default -> null; };
            if (local == null) return new NotVerified("asset of kind '" + a.kind() + "' has no local file to compare with: " + a.url());
            String remote;
            try { remote = download(a.url()); }
            catch (Exception e) { return new NotVerified("cannot download " + a.url() + ": " + e.getMessage()); }
            if (!remote.equalsIgnoreCase(local))
                return new NotVerified("the published " + a.kind() + " differs from the local one (" + Hashes.brief(remote) + " there, " + Hashes.brief(local) + " here)");
            if (a.kind().equals("exe")) exeOk = true; else jarOk = true;
        }
        if (!exeOk) return new NotVerified("the exe itself is not among the verified assets");
        if (r.jar() != null && !jarOk && !r.jarSha256Pinned()) return new NotVerified("the jar is neither a verified asset nor pinned by the exe's own config");
        return new Verified(p.group(1), p.group(2), tag, Instant.now());
    }

    String download(String url) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).header("User-Agent", "leasegate").GET().build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (var in = resp.body()) {
            if (resp.statusCode() != 200) throw new java.io.IOException("HTTP " + resp.statusCode());
            if (!"https".equals(resp.uri().getScheme())) throw new java.io.IOException("redirected off https");
            return Hashes.sha256(in, LIMIT);
        }
    }
}
