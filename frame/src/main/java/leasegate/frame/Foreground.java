package leasegate.frame;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;

/// Whether a window is the one the keyboard types into. JavaFX's own focused flag is not that: when
/// a window pops up while the human is typing elsewhere, Windows refuses to bring it to the front
/// (focus-stealing prevention), yet JavaFX reports it focused. A window that glows then invites a
/// passphrase that lands in another program -- often a terminal whose transcript is kept. So on
/// Windows the answer comes from the OS itself; elsewhere JavaFX's flag is used as it is.
final class Foreground {

    static final MethodHandle GET_FOREGROUND = TaskbarButton.WINDOWS ? lookup() : null;

    /// The native handle of this process's window with that title, or 0 if not found / not Windows.
    static long handle(String title) {
        if (!TaskbarButton.WINDOWS) return 0;
        try (var arena = Arena.ofConfined()) { return TaskbarButton.ownWindow(arena, title).address(); }
        catch (Throwable t) { return 0; }
    }

    /// True when `hwnd` is the foreground window. Unknown (0, or not Windows) counts as true, so a
    /// failure here never greys a window that really is in front.
    static boolean isFront(long hwnd) {
        if (hwnd == 0 || GET_FOREGROUND == null) return true;
        try { return ((MemorySegment) GET_FOREGROUND.invoke()).address() == hwnd; }
        catch (Throwable t) { return true; }
    }

    static MethodHandle lookup() {
        try {
            var user32 = SymbolLookup.libraryLookup("user32", Arena.global());
            return Linker.nativeLinker().downcallHandle(user32.findOrThrow("GetForegroundWindow"), FunctionDescriptor.of(ADDRESS));
        } catch (Throwable t) { return null; }
    }

    private Foreground() {}
}
