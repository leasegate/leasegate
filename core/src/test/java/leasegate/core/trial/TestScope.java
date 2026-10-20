package leasegate.core.trial;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import leasegate.core.access.*;

/// The smallest scope a trial needs: a set of named things, null meaning all of them.
public record TestScope(Set<String> items, Integer requestedMinutes) implements Scope<TestScope> {

    public static final Path RUN_DIR = Path.of(System.getProperty("java.io.tmpdir"), "leasegate-trial-run");
    public static final TestScope ALL = new TestScope(null, null);
    public static final LeaseRules<TestScope> RULES = new Rules();

    public static TestScope of(String... items) { return new TestScope(Set.of(items), null); }

    @Override public boolean covers(TestScope r) { return items == null || r.items != null && items.containsAll(r.items); }

    @Override public TestScope narrowedTo(TestScope r) { return new TestScope(r.items != null ? r.items : items, r.requestedMinutes); }

    @Override public int narrowness() { return items == null ? 0 : 1; }

    static final class Rules implements LeaseRules<TestScope> {
        @Override public String product() { return "trialgate"; }
        @Override public int keyedMaxMinutes(TestScope s) { return s.unscoped() ? 240 : 7 * 1440; }
        @Override public int riskBase(TestScope s) { return s.unscoped() ? 2 : s.items().size() == 1 ? 0 : 1; }
        @Override public List<ScopeLine> describe(TestScope s) { return List.of(summary(s)); }
        @Override public ScopeLine summary(TestScope s) { return s.unscoped() ? ScopeLine.wide("items: ALL") : ScopeLine.narrow("items: " + String.join(", ", s.items())); }
        @Override public void writeScope(ObjectNode n, TestScope s) { if (s.items() != null) s.items().forEach(n.putArray("items")::add); }
        @Override public TestScope readScope(JsonNode n) {
            var l = Handshake.strings(n, "items");
            return new TestScope(l == null ? null : Set.copyOf(l), null);
        }
        @Override public TestScope stored(TestScope s) { return new TestScope(s.items(), null); }
    }
}
