package leasegate.core.access;

import java.util.Optional;

/// Tier 2 of the passphrase gating: while blanket mode is on, every request is approved with no
/// window, whatever it asks for, for whatever is left of the duration chosen when it was switched
/// on (four hours at most), and attested grants are anchored at the client itself so even this
/// mode stays per-process. For testing only. With blanket mode off, the real approval window decides.
public final class BlanketModeApproval<S extends Scope<S>> implements Approval<S> {

    final Approval<S> delegate;
    final PassphraseGate passphraseGate;

    public BlanketModeApproval(Approval<S> delegate, PassphraseGate passphraseGate) {
        this.delegate = delegate;
        this.passphraseGate = passphraseGate;
    }

    @Override public Optional<Decision<S>> decide(Request<S> r) {
        if (passphraseGate.isBlanketModeActive() && !r.chain().isEmpty()) {
            var minutes = (int) Math.max(1, passphraseGate.blanketModeRemaining().toMinutes());
            var anchor = r.kind() == Kind.ATTESTED ? r.chain().getFirst() : null;
            return Optional.of(new Decision<>(anchor, minutes, PassphraseGate.BLANKET_MODE_MAX_MINUTES, r.requested(), true));
        }
        return delegate.decide(r);
    }
}
