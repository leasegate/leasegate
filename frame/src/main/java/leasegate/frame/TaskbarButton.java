package leasegate.frame;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.*;

/// Takes a window's button off the Windows taskbar (ITaskbarList::DeleteTab). Opt-in only
/// (GateFrame.notInTaskbar): on Windows 11 the window then also drops out of Alt+Tab (found
/// 2026-10-20), and a prompt that cannot be Alt+Tabbed to is lost the moment it goes behind something.
/// Best effort, Windows only: if anything here fails, the window simply keeps its button.
final class TaskbarButton {

    static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    // {56FDF344-FD6D-11d0-958A-006097C9A090} and {56FDF342-FD6D-11d0-958A-006097C9A090}
    static final byte[] CLSID_TASKBARLIST = guid(0x56FDF344, (short) 0xFD6D, (short) 0x11d0, 0x95, 0x8A, 0x00, 0x60, 0x97, 0xC9, 0xA0, 0x90);
    static final byte[] IID_ITASKBARLIST = guid(0x56FDF342, (short) 0xFD6D, (short) 0x11d0, 0x95, 0x8A, 0x00, 0x60, 0x97, 0xC9, 0xA0, 0x90);
    static final int HR_INIT = 3, DELETE_TAB = 5, RELEASE = 2;

    /// Call on the FX thread after the window is shown; `title` must be the window's current title.
    static void remove(String title) {
        if (!WINDOWS) return;
        try (var arena = Arena.ofConfined()) {
            var hwnd = ownWindow(arena, title);
            if (hwnd.equals(MemorySegment.NULL)) return;
            var linker = Linker.nativeLinker();
            var ole32 = SymbolLookup.libraryLookup("ole32", arena);
            call(linker, ole32, "CoInitializeEx", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)).invoke(MemorySegment.NULL, 2);
            var out = arena.allocate(ADDRESS);
            int hr = (int) call(linker, ole32, "CoCreateInstance", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS))
                .invoke(arena.allocateFrom(JAVA_BYTE, CLSID_TASKBARLIST), MemorySegment.NULL, 1, arena.allocateFrom(JAVA_BYTE, IID_ITASKBARLIST), out);
            if (hr != 0) return;
            var list = out.get(ADDRESS, 0).reinterpret(ADDRESS.byteSize());
            var vtable = list.get(ADDRESS, 0).reinterpret(ADDRESS.byteSize() * 8);
            method(linker, vtable, HR_INIT, FunctionDescriptor.of(JAVA_INT, ADDRESS)).invoke(list);
            method(linker, vtable, DELETE_TAB, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)).invoke(list, hwnd);
            method(linker, vtable, RELEASE, FunctionDescriptor.of(JAVA_INT, ADDRESS)).invoke(list);
        } catch (Throwable ignored) {
            // the window keeps its taskbar button
        }
    }

    /// The top-level window of this process with that exact title.
    static MemorySegment ownWindow(Arena arena, String title) throws Throwable {
        var linker = Linker.nativeLinker();
        var user32 = SymbolLookup.libraryLookup("user32", arena);
        var find = call(linker, user32, "FindWindowExW", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        var pidOf = call(linker, user32, "GetWindowThreadProcessId", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        var name = arena.allocateFrom(title, StandardCharsets.UTF_16LE);
        var pid = arena.allocate(JAVA_INT);
        var me = ProcessHandle.current().pid();
        var hwnd = MemorySegment.NULL;
        while (true) {
            hwnd = (MemorySegment) find.invoke(MemorySegment.NULL, hwnd, MemorySegment.NULL, name);
            if (hwnd.equals(MemorySegment.NULL)) return hwnd;
            pidOf.invoke(hwnd, pid);
            if (Integer.toUnsignedLong(pid.get(JAVA_INT, 0)) == me) return hwnd;
        }
    }

    static MethodHandle call(Linker linker, SymbolLookup lib, String name, FunctionDescriptor fd) {
        return linker.downcallHandle(lib.findOrThrow(name), fd);
    }

    static MethodHandle method(Linker linker, MemorySegment vtable, int index, FunctionDescriptor fd) {
        return linker.downcallHandle(vtable.getAtIndex(ADDRESS, index), fd);
    }

    static byte[] guid(int d1, short d2, short d3, int... d4) {
        var b = new byte[16];
        for (int i = 0; i < 4; i++) b[i] = (byte) (d1 >>> (8 * i));
        b[4] = (byte) d2; b[5] = (byte) (d2 >>> 8);
        b[6] = (byte) d3; b[7] = (byte) (d3 >>> 8);
        for (int i = 0; i < 8; i++) b[8 + i] = (byte) d4[i];
        return b;
    }

    private TaskbarButton() {}
}
