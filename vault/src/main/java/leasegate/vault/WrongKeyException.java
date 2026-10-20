package leasegate.vault;

/// The passphrase or recovery key does not open this vault.
public final class WrongKeyException extends RuntimeException {

    public WrongKeyException() { super("that key does not open this vault"); }
}
