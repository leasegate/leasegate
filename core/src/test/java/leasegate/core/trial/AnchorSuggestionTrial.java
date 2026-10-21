package leasegate.core.trial;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import leasegate.core.access.AncestryNode;
import leasegate.core.access.AnchorSuggestion;
import leasegate.core.access.ProcessTree;

/// The suggested anchor is the agent session a request comes from, when there is one: an agent outlives
/// every command it runs, so an approval anchored there covers the next command too. Before this, the
/// suggestion was the nearest ancestor a few seconds older than the client, which in an agent's command
/// runner is often a wrapper shell that dies with the command, and the human had to move the anchor up
/// by hand every time. Without an agent in the chain the longevity rule still applies.
/// java -cp <classes> leasegate.core.trial.AnchorSuggestionTrial
public class AnchorSuggestionTrial {
    public static void main(String[] args) {
        var tree = ProcessTree.snapshot(); // only consulted for children of these pids, which do not exist
        var now = Instant.now();

        var withAgent = List.of(
            node(900_000_001L, "C:\\tools\\client.exe", now, 0),
            node(900_000_002L, "C:\\Git\\usr\\bin\\bash.exe", now.minusSeconds(1), 1),
            node(900_000_003L, "C:\\Git\\usr\\bin\\bash.exe", now.minusSeconds(10), 2),
            node(900_000_004L, "C:\\Users\\someone\\.local\\bin\\claude.exe", now.minus(Duration.ofMinutes(30)), 3),
            node(900_000_005L, "C:\\Windows\\System32\\cmd.exe", now.minus(Duration.ofHours(3)), 4),
            node(900_000_006L, "C:\\Apps\\Tabby\\Tabby.exe", now.minus(Duration.ofDays(1)), 22),
            node(900_000_007L, "C:\\Windows\\explorer.exe", now.minus(Duration.ofDays(2)), 43));
        check("with an agent in the chain, the agent is suggested, not the shell ten seconds older below it",
            AnchorSuggestion.suggest(withAgent, tree) == 3);

        var withoutAgent = List.of(
            node(900_000_011L, "C:\\tools\\client.exe", now, 0),
            node(900_000_012L, "C:\\Windows\\System32\\cmd.exe", now.minusSeconds(1), 1),
            node(900_000_013L, "C:\\Windows\\System32\\cmd.exe", now.minusSeconds(60), 2),
            node(900_000_014L, "C:\\Apps\\Tabby\\Tabby.exe", now.minus(Duration.ofDays(1)), 22),
            node(900_000_015L, "C:\\Windows\\explorer.exe", now.minus(Duration.ofDays(2)), 43));
        check("without an agent, the nearest ancestor materially older than the client is still suggested",
            AnchorSuggestion.suggest(withoutAgent, tree) == 2);

        System.out.println("ALL CHECKS PASSED");
    }

    static AncestryNode node(long pid, String path, Instant started, int descendants) {
        return new AncestryNode(pid, path, Optional.of(started), descendants);
    }

    static void check(String label, boolean condition) {
        if (!condition) throw new AssertionError("FAILED: " + label);
        System.out.println("ok - " + label);
    }
}
