package leasegate.core;

import java.nio.file.Path;
import java.util.function.Consumer;

import leasegate.core.posix.UnixSocketServer;
import leasegate.core.win.NamedPipeServer;
import leasegate.transport.LeasePlatform;

/// The accept loop every gate runs: one thread accepts, each connection gets its own thread. The
/// chain of the connecting process is walked by the handler first thing, before anything slower.
public final class LeaseServer {

    public static ConsumerServer open(Path runDir, String name) {
        return LeasePlatform.WINDOWS ? new NamedPipeServer(name) : new UnixSocketServer(LeasePlatform.socketPath(runDir, name));
    }

    /// Never returns.
    public static void serve(ConsumerServer server, String threadPrefix, Consumer<ConsumerServer.Accepted> handler) {
        while (true) {
            ConsumerServer.Accepted accepted;
            try { accepted = server.accept(); }
            catch (Exception e) { System.err.println(threadPrefix + ": accept failed: " + e.getMessage()); continue; }
            var a = accepted;
            Thread.ofPlatform().name(threadPrefix + "-client-" + a.peer().pid()).start(() -> handler.accept(a));
        }
    }

    private LeaseServer() {}
}
