package leasegate.core.trusted;

import leasegate.core.access.Scope;

/// The host's part of opening a long approval: given the passphrase the human just typed, take out
/// exactly what this approval needs (say, the credential of the accounts its scope covers), opening
/// the host's sealed store for that alone if the host is locked. Return `HeldMaterial.NONE` when the
/// host serves this app without keeping anything back. Throwing refuses the approval.
@FunctionalInterface
public interface MaterialSource<S extends Scope<S>> {

    HeldMaterial take(TrustedApp app, S scope, String passphrase);
}
