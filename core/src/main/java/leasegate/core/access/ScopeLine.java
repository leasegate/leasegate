package leasegate.core.access;

/// One line of "what is being asked for", as the approval and access windows show it. The tone
/// says how much it hands over, so the window can colour it without knowing what it means.
public record ScopeLine(String text, Tone tone) {

    public enum Tone { NARROW, WIDE, CAUTION, NOTE }

    public static ScopeLine narrow(String text) { return new ScopeLine(text, Tone.NARROW); }

    public static ScopeLine wide(String text) { return new ScopeLine(text, Tone.WIDE); }

    public static ScopeLine caution(String text) { return new ScopeLine(text, Tone.CAUTION); }

    public static ScopeLine note(String text) { return new ScopeLine(text, Tone.NOTE); }
}
