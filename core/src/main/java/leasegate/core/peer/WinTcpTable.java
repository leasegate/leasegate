package leasegate.core.peer;

import module java.base;

import static java.lang.foreign.ValueLayout.*;
import static leasegate.core.peer.TcpPeer.netPort;

/// GetExtendedTcpTable (IPv4, owner pid). The table is machine-wide, so a loopback connection is
/// in it twice, once from each side; the row wanted is the client's: local port theirs, remote ours.
final class WinTcpTable {

    static final int AF_INET = 2, TCP_TABLE_OWNER_PID_ALL = 5,
        NO_ERROR = 0, ERROR_INSUFFICIENT_BUFFER = 122, ESTABLISHED = 5;
    /// MIB_TCPTABLE_OWNER_PID: a count, then rows of six DWORDs.
    static final int ROWS_AT = 4, ROW = 24, STATE = 0, LOCAL_PORT = 8, REMOTE_PORT = 16, OWNER_PID = 20;

    static final MethodHandle GET_TABLE;
    static final String UNAVAILABLE;

    static {
        MethodHandle h = null;
        String why = null;
        try {
            var lib = SymbolLookup.libraryLookup("iphlpapi", Arena.global());
            h = Linker.nativeLinker().downcallHandle(lib.find("GetExtendedTcpTable").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
        } catch (Throwable t) {
            why = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        GET_TABLE = h;
        UNAVAILABLE = why;
    }

    static OptionalLong owner(int peerPort, int ourPort) throws Throwable {
        try (var arena = Arena.ofConfined()) {
            var table = read(arena);
            if (table == null) return OptionalLong.empty();
            var rows = table.get(JAVA_INT, 0);
            for (var i = 0; i < rows; i++) {
                var at = ROWS_AT + (long) i * ROW;
                if (table.get(JAVA_INT, at + STATE) != ESTABLISHED
                    || netPort(table, at + LOCAL_PORT) != peerPort
                    || netPort(table, at + REMOTE_PORT) != ourPort) continue;
                var pid = table.get(JAVA_INT, at + OWNER_PID) & 0xFFFFFFFFL;
                return pid == 0 ? OptionalLong.empty() : OptionalLong.of(pid);
            }
            return OptionalLong.empty();
        }
    }

    /// Size, allocate, read; twice, because the table can grow between the two calls.
    static MemorySegment read(Arena arena) throws Throwable {
        var size = arena.allocate(JAVA_INT);
        for (var attempt = 0; attempt < 2; attempt++) {
            size.set(JAVA_INT, 0, 0);
            var probe = (int) GET_TABLE.invokeExact(MemorySegment.NULL, size, 0, AF_INET, TCP_TABLE_OWNER_PID_ALL, 0);
            if (probe != ERROR_INSUFFICIENT_BUFFER) return null;
            var bytes = size.get(JAVA_INT, 0) & 0xFFFFFFFFL;
            if (bytes <= 0 || bytes > (1 << 26)) return null;
            var table = arena.allocate(bytes);
            var got = (int) GET_TABLE.invokeExact(table, size, 0, AF_INET, TCP_TABLE_OWNER_PID_ALL, 0);
            if (got == NO_ERROR) return table;
            if (got != ERROR_INSUFFICIENT_BUFFER) return null;
        }
        return null;
    }

    private WinTcpTable() {}
}
