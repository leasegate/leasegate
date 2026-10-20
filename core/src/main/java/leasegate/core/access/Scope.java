package leasegate.core.access;

/// What a connection may reach, in the gate's own terms: browser profiles and websites for a
/// browser gate, account, repository and operation for a git gate, host and key for an ssh gate.
/// The engine needs only three things from it: whether one scope fits inside another, what a
/// connection gets once it does, and how narrow it is -- because narrowness is what earns a long
/// approval. `requestedMinutes` is what the client asked for, shown to the human and never assumed.
public interface Scope<S extends Scope<S>> {

    /// Whether `requested` fits inside this approved scope.
    boolean covers(S requested);

    /// What a connection gets once `covers(requested)` holds: the request wherever it narrows,
    /// this approval elsewhere.
    S narrowedTo(S requested);

    Integer requestedMinutes();

    /// How many of the scope's dimensions are narrowed. 0 reaches everything the gate guards.
    int narrowness();

    default boolean unscoped() { return narrowness() == 0; }
}
