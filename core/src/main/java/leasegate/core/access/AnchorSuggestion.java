package leasegate.core.access;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/// Which ancestor the approval window selects first. Prefer the agent session the request comes
/// from: an agent outlives every command it runs, so an approval anchored there also covers the
/// next command, while the shell an agent wraps each command in dies with that command. Without an
/// agent in the chain, the design doc's principle applies: "prefer the shallowest ancestor that is
/// materially longer-lived than the client, and never suggest at or above the terminal host."
/// Either way only a hop whose cap is above zero is suggested. Shared by ConsoleApproval and the
/// JavaFX approval window so the two never drift apart.
public final class AnchorSuggestion {

    /// Returns the suggested hop index, or -1 if there is no agent and nothing above the client
    /// looks materially longer-lived (the human then has to pick by hand).
    public static int suggest(List<AncestryNode> chain, ProcessTree tree) {
        for (var hop = 1; hop < chain.size(); hop++) {
            var node = chain.get(hop);
            var category = CategoryClassifier.classify(node, hop, chain.size(), tree);
            if (category != AnchorCategory.AGENT_SESSION) continue;
            var cap = DurationCaps.compute(category, Hops.effective(chain, hop), node.descendantCount());
            if (cap.minutes() > 0) return hop;
        }
        for (var hop = 1; hop < chain.size(); hop++) {
            var node = chain.get(hop);
            var category = CategoryClassifier.classify(node, hop, chain.size(), tree);
            var cap = DurationCaps.compute(category, Hops.effective(chain, hop), node.descendantCount());
            if (cap.minutes() > 0 && materiallyLongerLived(chain.get(0), node)) return hop;
        }
        return -1;
    }

    static boolean materiallyLongerLived(AncestryNode client, AncestryNode candidate) {
        return ageSeconds(candidate) > ageSeconds(client) + 5;
    }

    static long ageSeconds(AncestryNode node) {
        return node.startInstant().map(s -> Duration.between(s, Instant.now()).toSeconds()).orElse(0L);
    }

    private AnchorSuggestion() {}
}
