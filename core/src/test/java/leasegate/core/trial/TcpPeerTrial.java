package leasegate.core.trial;

import module java.base;
import leasegate.core.peer.TcpPeer;

import static leasegate.core.trial.Checks.*;

/// A child process (curl.exe on Windows, nc on macOS) connects to a loopback server; the lookup
/// must name the child's pid, which the trial knows and the child never says.
/// java --enable-native-access=ALL-UNNAMED -cp <classes;test-classes> leasegate.core.trial.TcpPeerTrial
public class TcpPeerTrial {

    public static void main(String[] args) throws Exception {
        System.out.println(System.getProperty("os.name") + ", available=" + TcpPeer.available() + " " + TcpPeer.unavailableReason());
        check("a lookup exists on this OS", TcpPeer.available());
        try (var server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            var child = new ProcessBuilder(client(server.getLocalPort()))
                .redirectInput(ProcessBuilder.Redirect.PIPE).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            try (var conn = server.accept()) {
                for (var run = 1; run <= 3; run++) {
                    var t = System.nanoTime();
                    var owner = TcpPeer.ownerOf(conn);
                    System.out.printf("run %d: %s in %.2f ms (child %d, us %d)%n", run, owner,
                        (System.nanoTime() - t) / 1e6, child.pid(), ProcessHandle.current().pid());
                    check("run " + run + ": the kernel names the child", owner.equals(OptionalLong.of(child.pid())));
                }
                check("our own end is not mistaken for the client's",
                    TcpPeer.ownerOf(conn.getLocalPort(), conn.getPort()).equals(OptionalLong.of(ProcessHandle.current().pid())));
            } finally { child.destroyForcibly(); }
        }
        check("a connection nobody holds is empty", TcpPeer.ownerOf(1, 1).isEmpty());
        System.exit(summary());
    }

    static List<String> client(int port) {
        return System.getProperty("os.name").toLowerCase().contains("win")
            ? List.of("curl.exe", "-s", "-m", "30", "http://127.0.0.1:" + port + "/")
            : List.of("/usr/bin/nc", "127.0.0.1", String.valueOf(port));
    }
}
