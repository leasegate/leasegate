package leasegate.core;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import leasegate.transport.LeasePlatform;

/// A gate runs all day and does something a few times an hour. When it has been idle for a while,
/// this gives memory back: one full collection (with the Serial collector and a low
/// MaxHeapFreeRatio the heap then shrinks and is returned to the OS), then, on Windows, the
/// process's working set is emptied (`SetProcessWorkingSetSize(self, -1, -1)`), so what stays
/// committed is paged out rather than held in RAM. The next request pages back in what it needs, in
/// milliseconds. Trims once per idle stretch, not over and over.
public final class IdleTrim {

    static final Duration CHECK = Duration.ofSeconds(30);

    public static void start(String name, Supplier<Instant> lastActivity, Duration idleFor) {
        Thread.ofPlatform().name(name + "-idle-trim").daemon().start(() -> {
            var trimmedFor = Instant.EPOCH;
            while (true) {
                try { Thread.sleep(CHECK.toMillis()); } catch (InterruptedException e) { return; }
                var last = lastActivity.get();
                if (last.equals(trimmedFor) || Duration.between(last, Instant.now()).compareTo(idleFor) < 0) continue;
                trim();
                trimmedFor = last;
            }
        });
    }

    public static void trim() {
        System.gc();
        if (!LeasePlatform.WINDOWS) return;
        try { EMPTY.invoke(CURRENT.invoke(), -1L, -1L); } catch (Throwable ignored) {}
    }

    static final MethodHandle CURRENT, EMPTY;
    static {
        MethodHandle c = null, e = null;
        if (LeasePlatform.WINDOWS) {
            var k32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
            var linker = Linker.nativeLinker();
            c = linker.downcallHandle(k32.find("GetCurrentProcess").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS));
            e = linker.downcallHandle(k32.find("SetProcessWorkingSetSize").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        }
        CURRENT = c;
        EMPTY = e;
    }

    private IdleTrim() {}
}
