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
}
