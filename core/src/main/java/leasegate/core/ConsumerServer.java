package leasegate.core;

import leasegate.core.win.PeerIdentity;
import leasegate.transport.Links;

/// Where clients connect: the named pipe on Windows (`win.NamedPipeServer`), a Unix domain socket
/// elsewhere (`posix.UnixSocketServer`). Either way the peer is named by the kernel, never by the
/// client's own word -- that is the property the rest of the gate is built on. The link is both
/// framed and raw; the protocol on top decides which view it reads.
public interface ConsumerServer {

    /// Blocks until a client connects. Called in a loop from one accept thread.
    Accepted accept();

    record Accepted(PeerIdentity peer, Links.Duplex io) {}
}
