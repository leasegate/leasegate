package leasegate.transport;

/// The same link as FrameIo, read and written as plain bytes, for protocols that frame themselves
/// (the ssh agent's length-prefixed messages). A connection uses one view or the other, never both.
public interface RawIo extends AutoCloseable {

    /// Fills `buf` from `off` with up to `len` bytes; -1 at end of stream.
    int read(byte[] buf, int off, int len);

    void write(byte[] buf, int off, int len);

    default void write(byte[] buf) { write(buf, 0, buf.length); }

    /// Exactly `len` bytes, or null if the stream ended first.
    default byte[] readFully(int len) {
        var out = new byte[len];
        var got = 0;
        while (got < len) {
            var n = read(out, got, len - got);
            if (n < 0) return null;
            got += n;
        }
        return out;
    }

    @Override void close();
}
