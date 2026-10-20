package leasegate.transport.posix;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import leasegate.transport.LeaseDeniedException;

/// Opens the client end of a gate's Unix domain socket. A gate accepts on one thread and hands
/// each connection off at once, so unlike the Windows pipe there is no gap between instances to
/// retry across: a refused connect means nobody is listening.
public final class SocketClient {

    public static SocketChannelIo open(String product, Path path) {
        if (!Files.exists(path))
            throw new LeaseDeniedException(product + " is not running (no socket " + path + ")",
                "start " + product + "; it lives in the menu bar");
        try {
            return new SocketChannelIo(SocketChannel.open(UnixDomainSocketAddress.of(path)));
        } catch (IOException e) {
            throw new LeaseDeniedException(product + " is not running (socket " + path + " refused: " + e.getMessage() + ")",
                "start " + product + "; it lives in the menu bar. A socket file left by a crash is replaced when it starts");
        }
    }

    private SocketClient() {}
}
