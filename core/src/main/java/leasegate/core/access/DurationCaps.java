package leasegate.core.access;

/// The three independent caps combined by minimum, and which one bound the result -- so the
/// approval window can name the reason rather than leave it mysterious. These bands are heuristic
/// and tunable on purpose: the design does not depend on getting them exactly right, only on the
/// shape (broader anchor earns less time). Each gate adds a fourth, by scope, through LeaseRules.
public final class DurationCaps {

    public record Result(int minutes, String boundBy) {}

    public static Result compute(AnchorCategory category, int hopsFromRequester, int descendantCount) {
        var byCategory = category.capMinutes;
        var byHops = byHopDistance(hopsFromRequester);
        var byDescendants = byDescendantCount(descendantCount);

        var minutes = Math.min(byCategory, Math.min(byHops, byDescendants));
        var reason = minutes == byCategory ? "anchor category (" + category + ")"
            : minutes == byHops ? "distance from requesting process (" + hopsFromRequester + " hops)"
            : "live descendant count (" + descendantCount + ")";
        return new Result(minutes, reason);
    }

    /// The three structural caps, then the gate's own cap by scope.
    public static <S extends Scope<S>> Result compute(AnchorCategory category, int hopsFromRequester, int descendantCount,
                                                      S scope, LeaseRules<S> rules) {
        return rules.capByScope(compute(category, hopsFromRequester, descendantCount), scope);
    }

    static int byHopDistance(int hops) {
        if (hops <= 2) return 1440;
        if (hops == 3) return 480;
        if (hops == 4) return 120;
        if (hops == 5) return 60;
        return 30;
    }

    static int byDescendantCount(int count) {
        if (count < 2) return 1440;
        if (count < 5) return 120;
        if (count < 30) return 30;
        return 0;
    }

    private DurationCaps() {}
}
