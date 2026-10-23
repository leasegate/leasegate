package leasegate.core.peer;

import module java.base;

/// Which process owns the client end of a loopback TCP connection, asked of the kernel, never of
/// the client. A gate serving a Unix socket gets this from LOCAL_PEERPID; a gate serving TCP has
/// only the client's port, so it looks that port up: on Windows in the TCP table, on macOS by
/// walking libproc's socket fds. Linux is not handled.
///
/// Empty is an ordinary answer, not an error: another OS, another user's process (macOS only lets
/// a process look into its own user's), a client that already closed. Nothing may be refused on
/// the strength of it. IPv4 only, as gates bind 127.0.0.1.
///
/// No cache: ephemeral ports are reused, so a port-keyed cache would in time name the wrong
/// process. Look up once per accepted connection and keep the answer with the connection.
public final class TcpPeer {

    static final String OS = System.getProperty("os.name", "").toLowerCase();
    static final boolean WINDOWS = OS.contains("win"), MAC = OS.contains("mac");

    public static boolean available() {
        return WINDOWS ? WinTcpTable.UNAVAILABLE == null : MAC && MacLibproc.UNAVAILABLE == null;
    }

    public static String unavailableReason() {
        return WINDOWS ? Objects.toString(WinTcpTable.UNAVAILABLE, "")
             : MAC ? Objects.toString(MacLibproc.UNAVAILABLE, "")
             : "no TCP owner lookup on " + OS;
    }

    /// The owner of the client end of a connection this process accepted.
    public static OptionalLong ownerOf(Socket accepted) {
        return ownerOf(accepted.getPort(), accepted.getLocalPort());
    }

    /// The pid whose ESTABLISHED IPv4 socket has local port `peerPort` and remote port `ourPort`.
    public static OptionalLong ownerOf(int peerPort, int ourPort) {
        if (peerPort <= 0 || ourPort <= 0 || !available()) return OptionalLong.empty();
        try {
            return WINDOWS ? WinTcpTable.owner(peerPort, ourPort) : MacLibproc.owner(peerPort, ourPort);
        } catch (Throwable t) {
            System.err.println("leasegate: could not look up the owner of a TCP connection: " + t);
            return OptionalLong.empty();
        }
    }

    /// A port held in network byte order in the low half of an int, as both kernels store it.
    static int netPort(MemorySegment s, long at) {
        var raw = s.get(ValueLayout.JAVA_INT, at);
        return ((raw & 0xFF) << 8) | ((raw >>> 8) & 0xFF);
    }

    private TcpPeer() {}
}
