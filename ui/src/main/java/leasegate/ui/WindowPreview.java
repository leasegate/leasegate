package leasegate.ui;

import leasegate.core.access.*;

/// Opens the approval and trust windows with nothing behind them -- every decision goes nowhere --
/// so a gate's own trial can render them with its own rules and look at the result. Call on the FX
/// Application Thread.
public final class WindowPreview {

    public static <S extends Scope<S>> void approval(Approval.Request<S> r, PassphraseGate gate, LeaseRules<S> rules) {
        new ApprovalWindow<>(r, gate, rules, d -> {}).show();
    }

    public static <S extends Scope<S>> void trust(TrustApproval.Request<S> r, PassphraseGate gate, LeaseRules<S> rules) {
        new TrustWindow<>(r, gate, rules, d -> {}).show();
    }

    private WindowPreview() {}
}
