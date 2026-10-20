package leasegate.transport.win;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

import static leasegate.transport.win.Kernel32.*;
import static leasegate.transport.win.Win32.*;

/// NUL-delimited UTF-8 frames (or, through RawIo, plain bytes) over raw HANDLEs.
///
/// Two transports, two I/O modes. A browser started with --remote-debugging-pipe gives two SEPARATE
/// unidirectional anonymous pipes (fd3 to write, fd4 to read) -- different kernel objects, so
/// plain synchronous ReadFile/WriteFile is fine; a read pending on one can never block a write on
/// the other. A gate's own consumer-facing named pipe is ONE duplex handle, and that case needs
/// real OVERLAPPED I/O: a synchronous WriteFile from one thread blocks for as long as ANOTHER
/// thread has a synchronous ReadFile already pending on the SAME pipe object -- reproduced and
/// confirmed 2026-09-20, and confirmed NOT fixed merely by duplicating the handle value (the
/// serialization is per pipe object, not per handle). Overlapped I/O is the real, Windows-native
/// way to have a pending read and a pending write coexist on one handle.
public final class PipeIo implements leasegate.transport.Links.Duplex {

    static final int INITIAL_BUF = 65536;

    final Arena arena = Arena.ofShared();
    final MemorySegment readHandle, writeHandle;
    final boolean overlapped;
    final MemorySegment readBuf = arena.allocate(INITIAL_BUF);
    final MemorySegment readCount = arena.allocate(ValueLayout.JAVA_INT);
    final MemorySegment writeCount = arena.allocate(ValueLayout.JAVA_INT);
    final MemorySegment readCapture = newCaptureSegment(arena);
    final MemorySegment writeCapture = newCaptureSegment(arena);
    final MemorySegment readOverlapped, writeOverlapped;   // null unless overlapped
    MemorySegment writeBuf = arena.allocate(INITIAL_BUF);
    byte[] carry = new byte[0];
    int carryPos;
    final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();

    public PipeIo(MemorySegment duplexHandle) {
        this.readHandle = duplexHandle;
        this.writeHandle = duplexHandle;
        this.overlapped = true;
        this.readOverlapped = newOverlapped(arena);
        this.writeOverlapped = newOverlapped(arena);
    }

    public PipeIo(MemorySegment readHandle, MemorySegment writeHandle) {
        this.readHandle = readHandle;
        this.writeHandle = writeHandle;
        this.overlapped = false;
        this.readOverlapped = null;
        this.writeOverlapped = null;
    }

    @Override public void writeFrame(String json) {
        var bytes = (json + "\0").getBytes(StandardCharsets.UTF_8);
        write(bytes, 0, bytes.length);
    }

    @Override public synchronized void write(byte[] bytes, int off, int len) {
        if (len > writeBuf.byteSize()) writeBuf = arena.allocate(len);
        MemorySegment.copy(bytes, off, writeBuf, ValueLayout.JAVA_BYTE, 0, len);
        try {
            if (overlapped) {
                clearOverlapped(writeOverlapped);
                var ok = (int) WriteFile.invoke(writeCapture, writeHandle, writeBuf, len,
                    MemorySegment.NULL, writeOverlapped);
                awaitOverlapped(writeHandle, writeOverlapped, writeCapture, writeCount, ok, "WriteFile");
            } else {
                if ((int) WriteFile.invoke(writeCapture, writeHandle, writeBuf, len,
                        writeCount, MemorySegment.NULL) == 0)
                    throw lastError("WriteFile", writeCapture);
            }
        } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new RuntimeException(t); }
    }

    /// One ReadFile on a byte-stream pipe can return several frames at once, or part of one;
    /// whatever follows a frame's NUL is kept for the next call rather than dropped.
    @Override public String readFrame() {
        var acc = new ByteArrayOutputStream();
        while (carryPos < carry.length) {
            var b = carry[carryPos++];
            if (b == 0) return acc.toString(StandardCharsets.UTF_8);
            acc.write(b);
        }
        while (true) {
            int got;
            try { got = readChunk(); }
            catch (RuntimeException e) { if (closed.get() || peerGone(e)) return null; throw e; }
            if (got <= 0) return null;
            var chunk = readBuf.asSlice(0, got).toArray(ValueLayout.JAVA_BYTE);
            for (var i = 0; i < got; i++) {
                if (chunk[i] == 0) { carry = chunk; carryPos = i + 1; return acc.toString(StandardCharsets.UTF_8); }
                acc.write(chunk[i]);
            }
        }
    }

    /// Plain bytes. A client that has gone (ERROR_BROKEN_PIPE) reads as end of stream.
    @Override public int read(byte[] buf, int off, int len) {
        if (carryPos < carry.length) {
            var n = Math.min(len, carry.length - carryPos);
            System.arraycopy(carry, carryPos, buf, off, n);
            carryPos += n;
            return n;
        }
        int got;
        try { got = readChunk(); }
        catch (RuntimeException e) { if (closed.get() || peerGone(e)) return -1; throw e; }
        if (got <= 0) return -1;
        var chunk = readBuf.asSlice(0, got).toArray(ValueLayout.JAVA_BYTE);
        var n = Math.min(len, got);
        System.arraycopy(chunk, 0, buf, off, n);
        if (n < got) { carry = chunk; carryPos = n; }
        return n;
    }

    static boolean peerGone(RuntimeException e) {
        var m = String.valueOf(e.getMessage());
        return m.endsWith("=" + ERROR_BROKEN_PIPE) || m.endsWith("=" + ERROR_NO_DATA) || m.endsWith("=" + ERROR_PIPE_NOT_CONNECTED);
    }

    int readChunk() {
        try {
            if (overlapped) {
                clearOverlapped(readOverlapped);
                var ok = (int) ReadFile.invoke(readCapture, readHandle, readBuf, INITIAL_BUF,
                    MemorySegment.NULL, readOverlapped);
                return awaitOverlapped(readHandle, readOverlapped, readCapture, readCount, ok, "ReadFile");
            }
            if ((int) ReadFile.invoke(readCapture, readHandle, readBuf, INITIAL_BUF,
                    readCount, MemorySegment.NULL) == 0)
                throw lastError("ReadFile", readCapture);
            return readCount.get(ValueLayout.JAVA_INT, 0);
        } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new RuntimeException(t); }
    }

    /// OVERLAPPED is 32 bytes on x64: Internal(8) InternalHigh(8) Offset/Pointer union(8) hEvent(8).
    /// One event per direction, reused across calls -- readFrame/writeFrame each fully complete
    /// before the next call on that same direction, so reuse is safe; read and write have their
    /// OWN event, which is exactly what lets them be pending at the same time.
    static MemorySegment newOverlapped(Arena arena) {
        var ov = arena.allocate(32);
        ov.set(ValueLayout.ADDRESS, 24, createEvent(arena));
        return ov;
    }

    static void clearOverlapped(MemorySegment ov) {
        ov.set(ValueLayout.JAVA_LONG, 0, 0L);
        ov.set(ValueLayout.JAVA_LONG, 8, 0L);
        ov.set(ValueLayout.JAVA_LONG, 16, 0L);
    }

    /// ok!=0 means it already completed synchronously; ok==0 with ERROR_IO_PENDING means wait for
    /// it. Either way GetOverlappedResult(wait=true) is the one place the real byte count comes
    /// from -- the immediate call's own "bytes transferred" output is unreliable for overlapped
    /// handles per Win32 docs, so it is deliberately never read.
    int awaitOverlapped(MemorySegment handle, MemorySegment ov, MemorySegment capture,
                         MemorySegment countBox, int immediateOk, String what) {
        if (immediateOk == 0) {
            var err = lastErrorFrom(capture);
            if (err != ERROR_IO_PENDING) throw lastError(what, capture);
        }
        try {
            if ((int) GetOverlappedResult.invoke(capture, handle, ov, countBox, 1) == 0)
                throw lastError(what + "/GetOverlappedResult", capture);
        } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new RuntimeException(t); }
        return countBox.get(ValueLayout.JAVA_INT, 0);
    }

    /// Aborts whatever ReadFile/WriteFile is currently pending on this pipe, from ANY thread,
    /// without closing the handle -- so the thread blocked in readFrame() wakes with an exception
    /// and unwinds through its own normal cleanup (which does the actual close()), rather than
    /// racing a cross-thread close() against native memory this object still owns.
    @Override public void cancelPendingIo() {
        if (closed.get()) return;
        try { CancelIoEx.invoke(readHandle, MemorySegment.NULL); } catch (Throwable ignored) {}
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { CloseHandle.invoke(readHandle); } catch (Throwable ignored) {}
        if (writeHandle.address() != readHandle.address()) {
            try { CloseHandle.invoke(writeHandle); } catch (Throwable ignored) {}
        }
        if (overlapped) {
            try { CloseHandle.invoke(readOverlapped.get(ValueLayout.ADDRESS, 24)); } catch (Throwable ignored) {}
            try { CloseHandle.invoke(writeOverlapped.get(ValueLayout.ADDRESS, 24)); } catch (Throwable ignored) {}
        }
        arena.close();
    }
}
