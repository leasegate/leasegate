package leasegate.core.access;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/// What one gate decides for itself, on top of the structural rules every gate shares (the anchor,
/// its distance, its live descendants). Everything here is about the scope, because the scope is
/// the one thing that means something different to every gate.
public interface LeaseRules<S extends Scope<S>> {

    /// The gate's name as the human sees it: in window titles, tray tooltips, refusals.
    String product();

    /// The fourth cap, by how much the request reaches. `base` is the minimum of the three
    /// structural caps; return it unchanged to add nothing.
    default DurationCaps.Result capByScope(DurationCaps.Result base, S scope) { return base; }

    /// The longest a keyed app may be approved for, by how narrowly it is scoped.
    int keyedMaxMinutes(S scope);

    /// 0 for the narrowest kind of request, 1 for anything between, 2 for everything.
    default int riskBase(S scope) { return scope.unscoped() ? 2 : 1; }

    /// Whether approving this request needs the passphrase typed again (tier 1).
    default boolean needsPassphrase(S scope) { return scope.unscoped(); }

    /// Why this request needs the passphrase, in a sentence the approval window shows beside the
    /// request and in the passphrase prompt. A gate whose passphrase guards something other than
    /// breadth (an action that cannot be undone, say) says so here.
    default String passphraseReason(S scope) { return "This request reaches everything this gate guards."; }

    /// The request, line by line, for the approval and trust windows.
    List<ScopeLine> describe(S scope);

    /// One line for the access list.
    ScopeLine summary(S scope);

    /// Writes the scope's fields into a keyed-app entry or a reply, and reads them back.
    void writeScope(ObjectNode into, S scope);

    S readScope(JsonNode from);

    /// The same scope with `requestedMinutes` dropped, as it is stored once approved.
    S stored(S scope);
}
