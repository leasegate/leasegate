package leasegate.transport;

import java.nio.file.Path;

import leasegate.transport.posix.SocketClient;
import leasegate.transport.win.PipeClient;

/// Opens the client end of a gate: the named pipe on Windows, the Unix domain socket elsewhere.
/// The returned object is both a FrameIo and a RawIo; a caller uses one view.
public final class Links {

    public interface Duplex extends FrameIo, RawIo {
        @Override void close();
    }

    /// `product` only words the error ("gitgate is not running ..."). Throws LeaseDeniedException.
    public static Duplex open(String product, Path runDir, String name) {
        return LeasePlatform.WINDOWS ? PipeClient.open(product, name) : SocketClient.open(product, LeasePlatform.socketPath(runDir, name));
    }

    private Links() {}
}
