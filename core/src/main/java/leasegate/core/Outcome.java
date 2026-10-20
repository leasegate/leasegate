package leasegate.core;

/// What the engine decided about one request: allowed, with the grant or keyed approval that
/// allows it, or refused, with what to tell the client and how it could do better.
public sealed interface Outcome<T> {

    record Allowed<T>(T value) implements Outcome<T> {}

    record Refused<T>(String error, String hint) implements Outcome<T> {}

    static <T> Outcome<T> allowed(T value) { return new Allowed<>(value); }

    static <T> Outcome<T> refused(String error, String hint) { return new Refused<>(error, hint); }
}
