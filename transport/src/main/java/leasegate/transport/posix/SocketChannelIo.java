package leasegate.transport.posix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import leasegate.transport.FrameIo;

/// The client end of a gate's Unix domain socket, on plain java.nio -- a client needs no
/// native code at all; only the server side, which must ask the kernel who connected, does.
/// A socket is full duplex, so a read pending on one thread never holds up a write on another
/// (the problem the Windows side needs overlapped I/O for does not exist here).
public final class SocketChannelIo implements leasegate.transport.Links.Duplex {

    final SocketChannel ch;
    final ByteBuffer in = ByteBuffer.allocate(65536).flip();
    final AtomicBoolean closed = new AtomicBoolean();

    public SocketChannelIo(SocketChannel ch) { this.ch = ch; }

    @Override public void writeFrame(String json) {
        var bytes = (json + "\0").getBytes(StandardCharsets.UTF_8);
        write(bytes, 0, bytes.length);
    }

    @Override public void write(byte[] bytes, int off, int len) {
        var buf = ByteBuffer.wrap(bytes, off, len);
        synchronized (this) {
            try { while (buf.hasRemaining()) ch.write(buf); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }

    @Override public int read(byte[] buf, int off, int len) {
        try {
            if (!in.hasRemaining()) {
                in.clear();
                var n = ch.read(in);
                in.flip();
                if (n < 0) return -1;
            }
            var n = Math.min(len, in.remaining());
            in.get(buf, off, n);
            return n;
        } catch (IOException e) {
            if (closed.get()) return -1;
            throw new UncheckedIOException(e);
        }
    }

    @Override public String readFrame() {
        var acc = new ByteArrayOutputStream();
        try {
            while (true) {
                while (in.hasRemaining()) {
                    var b = in.get();
                    if (b == 0) return acc.toString(StandardCharsets.UTF_8);
                    acc.write(b);
                }
                in.clear();
                var n = ch.read(in);
                in.flip();
                if (n < 0) return null;
            }
        } catch (IOException e) {
            if (closed.get()) return null;
            throw new UncheckedIOException(e);
        }
    }

    /// shutdownInput makes a blocked read return end-of-stream without closing the channel.
    @Override public void cancelPendingIo() {
        if (closed.get()) return;
        try { ch.shutdownInput(); } catch (IOException ignored) {}
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { ch.close(); } catch (IOException ignored) {}
    }
}
