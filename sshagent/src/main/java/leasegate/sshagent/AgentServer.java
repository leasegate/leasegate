package leasegate.sshagent;

import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import leasegate.core.ConsumerServer;
import leasegate.core.LeaseServer;
import leasegate.core.ProcInfo;
import leasegate.core.access.ProcessTree;

/// An ssh agent (draft-miller-ssh-agent) on the gate's own pipe or socket. It answers two requests:
/// which keys it holds (public halves only), and "sign this with that key" -- which goes through the
/// policy first. Everything else, including adding or removing keys over the protocol, is refused:
/// keys enter the gate only through the gate itself. The private key never leaves this process.
public final class AgentServer {

    static final int FAILURE = 5, SUCCESS = 6, REQUEST_IDENTITIES = 11, IDENTITIES_ANSWER = 12,
        SIGN_REQUEST = 13, SIGN_RESPONSE = 14, MAX_MESSAGE = 256 * 1024;

    final Supplier<List<AgentKey>> keys;
    final SignPolicy policy;

    public AgentServer(Supplier<List<AgentKey>> keys, SignPolicy policy) {
        this.keys = keys;
        this.policy = policy;
    }

    /// Never returns.
    public void serve(ConsumerServer server, String threadPrefix) {
        LeaseServer.serve(server, threadPrefix, this::handle);
    }

    void handle(ConsumerServer.Accepted a) {
        var raw = ProcessTree.rawChain(a.peer().pid());
        var allowed = new HashMap<String, Boolean>();
        var io = a.io();
        try {
            while (true) {
                var len = io.readFully(4);
                if (len == null) return;
                var n = ((len[0] & 0xff) << 24) | ((len[1] & 0xff) << 16) | ((len[2] & 0xff) << 8) | (len[3] & 0xff);
                if (n <= 0 || n > MAX_MESSAGE) return;
                var msg = io.readFully(n);
                if (msg == null) return;
                io.write(answer(msg, a, raw, allowed));
            }
        } catch (RuntimeException e) {
            System.err.println("agent: connection from pid " + a.peer().pid() + " ended: " + e.getMessage());
        } finally { io.close(); }
    }

    byte[] answer(byte[] msg, ConsumerServer.Accepted a, List<ProcessTree.RawLink> raw, HashMap<String, Boolean> allowed) {
        var r = new SshReader(msg);
        return switch (r.u8()) {
            case REQUEST_IDENTITIES -> identities(policy.offer(keys.get(), a.peer(), raw, ProcInfo.commandLine(a.peer().pid()).orElse("")));
            case SIGN_REQUEST -> {
                var blob = r.string();
                var data = r.string();
                var key = keys.get().stream().filter(k -> k.matches(blob)).findFirst();
                if (key.isEmpty()) yield failure();
                var ok = allowed.computeIfAbsent(key.get().fingerprint(), f -> policy.allow(new SignPolicy.Request(a.peer(), raw, key.get(),
                    ProcInfo.commandLine(a.peer().pid()).orElse(""))));
                yield ok ? new SshWriter().u8(SIGN_RESPONSE).string(key.get().sign(data)).framed() : failure();
            }
            default -> failure();
        };
    }

    byte[] identities(List<AgentKey> list) {
        var w = new SshWriter().u8(IDENTITIES_ANSWER).u32(list.size());
        for (var k : list) w.string(k.blob()).string(k.comment);
        return w.framed();
    }

    static byte[] failure() { return new SshWriter().u8(FAILURE).framed(); }
}
