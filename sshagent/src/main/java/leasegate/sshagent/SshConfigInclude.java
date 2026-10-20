package leasegate.sshagent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/// How a gate puts its ssh settings in front of OpenSSH without touching anything else in the
/// user's `~/.ssh/config`: the gate writes a file of its own (one `Host` block per host it serves)
/// and adds two lines at the top of the user's file -- a marker naming the gate, and an `Include`
/// of that file. Several gates do this side by side and never step on each other:
///
/// - a gate finds, adds and removes only the two lines under its OWN marker (`# leasegate:<gate>`);
/// - a gate reads the other gates' included files (found through their markers) and refuses to
///   claim a host another gate already serves, naming that gate;
/// - the user's own lines are never rewritten, only moved down by the two lines added at the top.
///
/// For one host ssh takes the first value it meets for each option, so a gate's block (included at
/// the top) decides the agent and key, while the user's own `Host` block still supplies HostName,
/// Port and User.
public final class SshConfigInclude {

    public record Block(List<String> hosts, List<String> options) {}

    static final String PREFIX = "# leasegate:";
    static final Pattern HOST = Pattern.compile("^\\s*Host\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    public static String marker(String gate) {
        return PREFIX + gate + " -- " + gate + " setup --undo removes this line and the next";
    }

    public static String include(Path own) { return "Include " + slash(own); }

    /// Writes the gate's own file from its blocks.
    public static void writeOwn(Path own, String gate, String source, List<Block> blocks) throws Exception {
        var out = new StringBuilder("# Written by " + gate + " from " + source + " -- edit that, not this.\n");
        for (var b : blocks) {
            out.append("\nHost ").append(String.join(" ", b.hosts())).append('\n');
            for (var o : b.options()) out.append("    ").append(o).append('\n');
        }
        Files.createDirectories(own.getParent());
        Files.writeString(own, out.toString());
    }

    /// Hosts that another gate's included file already serves: host -> that gate.
    public static Map<String, String> claimedByOthers(Path sshConfig, String gate) throws Exception {
        var out = new LinkedHashMap<String, String>();
        if (!Files.exists(sshConfig)) return out;
        var lines = Files.readAllLines(sshConfig);
        for (var i = 0; i + 1 < lines.size(); i++) {
            var line = lines.get(i).strip();
            if (!line.startsWith(PREFIX)) continue;
            var other = line.substring(PREFIX.length()).split("\\s")[0];
            if (other.equalsIgnoreCase(gate)) continue;
            var inc = lines.get(i + 1).strip();
            if (!inc.regionMatches(true, 0, "Include ", 0, 8)) continue;
            var file = Path.of(inc.substring(8).strip());
            if (!Files.exists(file)) continue;
            for (var l : Files.readAllLines(file)) {
                var m = HOST.matcher(l);
                if (m.matches()) for (var h : m.group(1).trim().split("\\s+")) out.putIfAbsent(h.toLowerCase(Locale.ROOT), other);
            }
        }
        return out;
    }

    /// The hosts of `mine` that another gate already serves, as readable lines; empty means none.
    public static List<String> conflicts(Path sshConfig, String gate, List<Block> mine) throws Exception {
        var others = claimedByOthers(sshConfig, gate);
        var out = new ArrayList<String>();
        for (var b : mine) for (var h : b.hosts()) {
            var who = others.get(h.toLowerCase(Locale.ROOT));
            if (who != null) out.add("host " + h + " is already served by " + who);
        }
        return out;
    }

    /// Adds the gate's two lines at the top of the user's file (backing it up first), unless they are
    /// there. Returns whether the file changed.
    public static boolean install(Path sshConfig, String gate, Path own) throws Exception {
        var text = Files.exists(sshConfig) ? Files.readString(sshConfig) : "";
        var mine = marker(gate) + "\n" + include(own) + "\n";
        if (text.contains(marker(gate))) {
            if (text.contains(mine)) return false;
            text = remove(text, gate);                      // an older form of our own two lines
        }
        Files.createDirectories(sshConfig.getParent());
        if (Files.exists(sshConfig)) Files.copy(sshConfig, backup(sshConfig, gate), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(sshConfig, mine + (text.startsWith(PREFIX) || text.isEmpty() ? "" : "\n") + text);
        return true;
    }

    /// Removes the gate's own two lines, nothing else. Returns whether the file changed.
    public static boolean undo(Path sshConfig, String gate) throws Exception {
        if (!Files.exists(sshConfig)) return false;
        var text = Files.readString(sshConfig);
        var after = remove(text, gate);
        if (after.equals(text)) return false;
        Files.writeString(sshConfig, after);
        return true;
    }

    /// Drops the marker line of `gate` and the Include line under it, and a blank line left behind at the top.
    static String remove(String text, String gate) {
        var lines = new ArrayList<>(List.of(text.split("\n", -1)));
        for (var i = 0; i < lines.size(); i++) {
            if (!lines.get(i).strip().startsWith(PREFIX + gate + " ")) continue;
            lines.remove(i);
            if (i < lines.size() && lines.get(i).strip().regionMatches(true, 0, "Include ", 0, 8)) lines.remove(i);
            i--;
        }
        var out = String.join("\n", lines);
        while (out.startsWith("\n")) out = out.substring(1);
        return out;
    }

    /// `config.before-<gate>`, or, when that already exists, the same name with a time stamp: an
    /// earlier backup is never overwritten.
    static Path backup(Path sshConfig, String gate) {
        var plain = sshConfig.resolveSibling("config.before-" + gate);
        if (!Files.exists(plain)) return plain;
        var stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return sshConfig.resolveSibling("config.before-" + gate + "." + stamp);
    }

    static String slash(Path p) { return p.toAbsolutePath().toString().replace('\\', '/'); }

    private SshConfigInclude() {}
}
