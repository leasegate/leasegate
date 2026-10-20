package leasegate.frame;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

import static java.lang.foreign.ValueLayout.*;

/// Whether the Windows taskbar is dark (`SystemUsesLightTheme` = 0), so the tray and window icons
/// can use the set drawn for a dark background. Windows sends no message a JavaFX app can see when
/// the theme changes, so the value is read again every two seconds; one registry read is cheap.
/// Anywhere but Windows, or if the read fails, the taskbar counts as light.
public final class TaskbarTheme {

    static final MemorySegment HKEY_CURRENT_USER = MemorySegment.ofAddress(0xFFFFFFFF80000001L);
    static final int RRF_RT_REG_DWORD = 0x10;
    static final MethodHandle REG_GET_VALUE = TaskbarButton.WINDOWS ? lookup() : null;
    static ReadOnlyBooleanWrapper dark;

    /// Call on the FX thread. The first call starts the two-second check.
    public static ReadOnlyBooleanProperty darkProperty() {
        if (dark == null) {
            dark = new ReadOnlyBooleanWrapper(readDark());
            // A daemon thread rather than an endless Timeline: a running animation keeps JavaFX's frame clock going (PRP 10).
            if (REG_GET_VALUE != null) Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().daemon().name("taskbar-theme").unstarted(r))
                .scheduleWithFixedDelay(() -> { var now = readDark(); Platform.runLater(() -> dark.set(now)); }, 2, 2, TimeUnit.SECONDS);
        }
        return dark.getReadOnlyProperty();
    }

    public static boolean isDark() { return darkProperty().get(); }

    /// Reads the registry now; safe on any thread.
    public static boolean readDark() {
        if (REG_GET_VALUE == null) return false;
        try (var arena = Arena.ofConfined()) {
            var value = arena.allocate(JAVA_INT);
            var size = arena.allocateFrom(JAVA_INT, 4);
            int rc = (int) REG_GET_VALUE.invoke(HKEY_CURRENT_USER,
                arena.allocateFrom("Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", StandardCharsets.UTF_16LE),
                arena.allocateFrom("SystemUsesLightTheme", StandardCharsets.UTF_16LE),
                RRF_RT_REG_DWORD, MemorySegment.NULL, value, size);
            return rc == 0 && value.get(JAVA_INT, 0) == 0;
        } catch (Throwable e) {
            return false;
        }
    }

    static MethodHandle lookup() {
        try {
            var advapi32 = SymbolLookup.libraryLookup("advapi32", Arena.global());
            return Linker.nativeLinker().downcallHandle(advapi32.findOrThrow("RegGetValueW"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        } catch (Throwable e) {
            return null;
        }
    }

    private TaskbarTheme() {}
}
