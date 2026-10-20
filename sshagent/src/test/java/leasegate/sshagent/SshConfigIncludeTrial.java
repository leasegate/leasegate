package leasegate.sshagent;

import java.nio.file.Files;
import java.util.List;

/// Two gates in one ~/.ssh/config (a scratch copy): each adds and removes only its own two lines,
/// a host one gate serves cannot be claimed by the other, and the user's own lines stay as they were.
/// java -cp <classes> leasegate.sshagent.SshConfigIncludeTrial
public class SshConfigIncludeTrial {

    static int failed;

    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("ssh-include-trial");
        var config = dir.resolve(".ssh").resolve("config");
        Files.createDirectories(config.getParent());
        var userOwn = "Host box\n    HostName box.example\n    User admin\n";
        Files.writeString(config, userOwn);
        var forgeFile = dir.resolve("forge/ssh_config");
        var serverFile = dir.resolve("server/ssh_config");
        var forge = List.of(new SshConfigInclude.Block(List.of("forge.example"), List.of("IdentityAgent //./pipe/forge-agent")));
        var server = List.of(new SshConfigInclude.Block(List.of("box"), List.of("IdentityAgent //./pipe/server-agent", "IdentitiesOnly yes")));

        SshConfigInclude.writeOwn(forgeFile, "forgegate", "forge settings", forge);
        check("first gate installs its two lines", SshConfigInclude.install(config, "forgegate", forgeFile));
        check("installing again changes nothing", !SshConfigInclude.install(config, "forgegate", forgeFile));
        check("the second gate sees no clash for its own host", SshConfigInclude.conflicts(config, "servergate", server).isEmpty());
        SshConfigInclude.writeOwn(serverFile, "servergate", "server settings", server);
        check("second gate installs its two lines", SshConfigInclude.install(config, "servergate", serverFile));
        var text = Files.readString(config);
        check("both gates' lines are at the top, the user's block intact below",
            text.contains(SshConfigInclude.marker("forgegate")) && text.contains(SshConfigInclude.marker("servergate")) && text.endsWith(userOwn));

        var clash = List.of(new SshConfigInclude.Block(List.of("FORGE.example"), List.of("IdentityAgent x")));
        var c = SshConfigInclude.conflicts(config, "servergate", clash);
        check("a host the other gate serves is refused, naming that gate (case-insensitive)", c.size() == 1 && c.getFirst().contains("forgegate"));
        check("a gate never counts its own hosts as a clash", SshConfigInclude.conflicts(config, "forgegate", forge).isEmpty());

        check("first gate's undo changes the file", SshConfigInclude.undo(config, "forgegate"));
        text = Files.readString(config);
        check("...and removes only its own lines", !text.contains("forgegate") && text.contains(SshConfigInclude.marker("servergate")) && text.endsWith(userOwn));
        Files.writeString(config.resolveSibling("config.before-again"), "older backup");
        SshConfigInclude.install(config, "again", forgeFile);
        check("an existing backup is never overwritten", Files.readString(config.resolveSibling("config.before-again")).equals("older backup")
            && Files.list(config.getParent()).anyMatch(p -> p.getFileName().toString().startsWith("config.before-again.")));
        SshConfigInclude.undo(config, "again");
        check("second gate's undo leaves exactly the user's file", SshConfigInclude.undo(config, "servergate") && Files.readString(config).equals(userOwn));
        check("an undo with nothing to remove changes nothing", !SshConfigInclude.undo(config, "servergate"));

        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) failed++;
    }
}
