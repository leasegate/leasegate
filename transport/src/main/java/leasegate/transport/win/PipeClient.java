package leasegate.transport.win;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

import leasegate.transport.LeaseDeniedException;
import leasegate.transport.LeasePlatform;

import static leasegate.transport.win.Kernel32.*;
import static leasegate.transport.win.Win32.*;

/// Opens the client end of a gate's named pipe. Kept apart from Links so that nothing on another
/// platform ever initialises Kernel32 (whose static initialiser looks up kernel32.dll).
public final class PipeClient {

    /// A gate creates the next pipe instance only after handing the last one to a connection, so a
    /// client arriving in between sees "not found" (2) or "busy" (231) for a moment; both are retried
    /// briefly before concluding that the gate is not running.
    public static PipeIo open(String product, String pipeName) {
        var path = LeasePlatform.pipePath(pipeName);
        var deadline = System.currentTimeMillis() + 3000;
        try (var scratch = Arena.ofConfined()) {
            var capture = newCaptureSegment(scratch);
            var name = scratch.allocateFrom(path, StandardCharsets.UTF_16LE);
            MemorySegment handle;
            while (true) {
                try {
                    handle = (MemorySegment) CreateFileW.invoke(capture, name, GENERIC_READ | GENERIC_WRITE, 0,
                        MemorySegment.NULL, OPEN_EXISTING, FILE_FLAG_OVERLAPPED, MemorySegment.NULL);
                } catch (Throwable t) { throw new RuntimeException(t); }
                var code = lastErrorFrom(capture);
                if (!isInvalid(handle) || (code != 2 && code != 231) || System.currentTimeMillis() > deadline) break;
                try { Thread.sleep(40); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (isInvalid(handle)) {
                var err = lastError("CreateFileW(" + path + ")", capture);
                throw new LeaseDeniedException(product + " is not running (no pipe " + path + ")",
                    "start " + product + "; it lives in the tray. " + err.getMessage());
            }
            return new PipeIo(handle);
        }
    }

    private PipeClient() {}
}
