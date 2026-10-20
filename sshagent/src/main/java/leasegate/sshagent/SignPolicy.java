package leasegate.sshagent;

import java.util.List;

import leasegate.core.access.ProcessTree;
import leasegate.core.win.PeerIdentity;

/// Whether one ssh process may have one key sign for it. Asked once per connection and key: the
/// gate answers from its grants or asks the human. `sshCommandLine` is the connecting ssh's own
/// command line, read from the operating system, which names the host and, for git, the
/// repository and whether it is a fetch or a push.
public interface SignPolicy {

    record Request(PeerIdentity peer, List<ProcessTree.RawLink> chain, AgentKey key, String sshCommandLine) {}

    boolean allow(Request request);

    /// Which keys to list to this ssh process. ssh offers a server every key the agent lists, so a
    /// gate holding keys of several identities for one server (two accounts on one forge) lists only
    /// the one that belongs to what this ssh is about to do. Default: all of them.
    default List<AgentKey> offer(List<AgentKey> all, PeerIdentity peer, List<ProcessTree.RawLink> chain, String sshCommandLine) { return all; }
}
