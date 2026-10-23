package leasegate.core.peer;

import module java.base;

import static java.lang.foreign.ValueLayout.*;
import static leasegate.core.peer.TcpPeer.netPort;

/// libproc: every pid, each pid's fds, each socket fd's socket_fdinfo, until one is the client's
/// end of the connection. proc_pidinfo answers only for processes of our own user, so another
/// user's client is simply not found.
///
/// The offsets are struct socket_fdinfo on macOS 26 (arm64), printed by clang from the SDK's
/// sys/proc_info.h with src/test/c/socket_fdinfo_offsets.c. Re-run it on a new macOS major version
/// before trusting them. A changed struct size makes proc_pidfdinfo return something other than
/// SOCKET_FDINFO bytes, which is skipped: a wrong layout gives empty, never a wrong pid.
final class MacLibproc {

    static final int PROC_ALL_PIDS = 1, PROC_PIDLISTFDS = 1, PROC_PIDFDSOCKETINFO = 3,
        PROX_FDTYPE_SOCKET = 2, PROC_FDINFO = 8, FD = 0, FDTYPE = 4;
    static final int SOCKET_FDINFO = 792, SOI_KIND = 256, FPORT = 264, LPORT = 268, VFLAG = 288, TCP_STATE = 344;
    static final int SOCKINFO_TCP = 2, INI_IPV4 = 1, TSI_S_ESTABLISHED = 4;

    static final MethodHandle LISTPIDS, PIDINFO, FDINFO;
    static final String UNAVAILABLE;

    static {
        MethodHandle a = null, b = null, c = null;
        String why = null;
        try {
            a = handle("proc_listpids", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
            b = handle("proc_pidinfo", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT));
            c = handle("proc_pidfdinfo", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
        } catch (Throwable t) {
            why = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        LISTPIDS = a; PIDINFO = b; FDINFO = c;
        UNAVAILABLE = why;
    }

    static MethodHandle handle(String name, FunctionDescriptor fd) {
        var linker = Linker.nativeLinker();
        return linker.downcallHandle(linker.defaultLookup().find(name)
            .orElseThrow(() -> new IllegalStateException("no " + name)), fd);
    }

    static OptionalLong owner(int peerPort, int ourPort) throws Throwable {
        try (var arena = Arena.ofConfined()) {
            var need = (int) LISTPIDS.invokeExact(PROC_ALL_PIDS, 0, MemorySegment.NULL, 0);
            if (need <= 0) return OptionalLong.empty();
            var pids = arena.allocate(need + 4096L);
            var got = (int) LISTPIDS.invokeExact(PROC_ALL_PIDS, 0, pids, (int) pids.byteSize());
            var info = arena.allocate(SOCKET_FDINFO);
            var fds = arena.allocate(16 * 1024L);
            for (var i = 0; i < got / 4; i++) {
                var pid = pids.get(JAVA_INT, 4L * i);
                if (pid <= 0) continue;
                var fdBytes = (int) PIDINFO.invokeExact(pid, PROC_PIDLISTFDS, 0L, MemorySegment.NULL, 0);
                if (fdBytes <= 0) continue;
                if (fdBytes + 1024L > fds.byteSize()) fds = arena.allocate(fdBytes + 1024L);
                var n = (int) PIDINFO.invokeExact(pid, PROC_PIDLISTFDS, 0L, fds, (int) fds.byteSize());
                for (var off = 0; off + PROC_FDINFO <= n; off += PROC_FDINFO) {
                    if (fds.get(JAVA_INT, off + FDTYPE) != PROX_FDTYPE_SOCKET) continue;
                    var r = (int) FDINFO.invokeExact(pid, fds.get(JAVA_INT, off + FD), PROC_PIDFDSOCKETINFO, info, SOCKET_FDINFO);
                    if (r == SOCKET_FDINFO && isClientEnd(info, peerPort, ourPort)) return OptionalLong.of(pid);
                }
            }
            return OptionalLong.empty();
        }
    }

    static boolean isClientEnd(MemorySegment info, int peerPort, int ourPort) {
        return info.get(JAVA_INT, SOI_KIND) == SOCKINFO_TCP
            && (info.get(JAVA_BYTE, VFLAG) & INI_IPV4) != 0
            && info.get(JAVA_INT, TCP_STATE) == TSI_S_ESTABLISHED
            && netPort(info, LPORT) == peerPort
            && netPort(info, FPORT) == ourPort;
    }

    private MacLibproc() {}
}
