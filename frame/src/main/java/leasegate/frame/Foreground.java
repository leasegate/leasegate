package leasegate.frame;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import javafx.stage.Stage;

import static java.lang.foreign.ValueLayout.ADDRESS;

/// Whether a window is the one the keyboard types into. JavaFX's own focused flag is not that: when
/// a window pops up while the human is typing elsewhere, Windows refuses to bring it to the front
/// (focus-stealing prevention), yet JavaFX reports it focused. A window that glows then invites a
/// passphrase that lands in another program -- often a terminal whose transcript is kept. So on
/// Windows the answer comes from the OS itself; elsewhere JavaFX's flag is used as it is.
final class Foreground {

    static final MethodHandle GET_FOREGROUND = TaskbarButton.WINDOWS ? lookup() : null;

    /// The native handle of this stage, or 0 if not found / not Windows. Found by title, so for that
    /// instant the title is made unique: two windows of one app often share a title (two approvals
    /// at once), and by title alone both would resolve to the first and glow together.
    static long handle(Stage stage) {
        if (!TaskbarButton.WINDOWS) return 0;
        var title = stage.getTitle();
        var probe = title + " ⁠" + System.nanoTime();
        try (var arena = Arena.ofConfined()) {
            stage.setTitle(probe);
            return TaskbarButton.ownWindow(arena, probe).address();
        } catch (Throwable t) {
            return 0;
        } finally {
            stage.setTitle(title);
        }
    }

    /// True when `hwnd` is the foreground window. Off Windows (no GetForegroundWindow) it is true,
    /// and JavaFX's flag decides. On Windows an unknown handle counts as false: a grey window that
    /// really is in front costs nothing, a glowing one that is not takes keys meant for it elsewhere.
    static boolean isFront(long hwnd) {
        if (GET_FOREGROUND == null) return true;
        if (hwnd == 0) return false;
        try { return ((MemorySegment) GET_FOREGROUND.invoke()).address() == hwnd; }
        catch (Throwable t) { return false; }
    }

    static MethodHandle lookup() {
        try {
            var user32 = SymbolLookup.libraryLookup("user32", Arena.global());
            return Linker.nativeLinker().downcallHandle(user32.findOrThrow("GetForegroundWindow"), FunctionDescriptor.of(ADDRESS));
        } catch (Throwable t) { return null; }
    }

    private Foreground() {}
}
