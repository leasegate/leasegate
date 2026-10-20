package leasegate.transport;

/// The gate refused, or could not be reached. `hint` is the gate's own advice on what to change.
public class LeaseDeniedException extends RuntimeException {

    public final String hint;

    public LeaseDeniedException(String message, String hint) {
        super(hint == null ? message : message + " -- " + hint);
        this.hint = hint;
    }
}
