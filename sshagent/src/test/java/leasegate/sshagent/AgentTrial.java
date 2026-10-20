package leasegate.sshagent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import leasegate.core.LeaseServer;

/// End-to-end proof against the real Windows OpenSSH tools, no network: an agent on a trial pipe
/// with a key made inside it; `ssh-add -L` lists it; `ssh-keygen -Y sign`, given only the PUBLIC
/// key file, gets the agent to sign; `ssh-keygen -Y check-novalidate` verifies that signature;
/// and with the policy saying no, signing fails. Also checks the fingerprint against ssh-keygen's.
/// java --enable-native-access=ALL-UNNAMED -cp <classes> leasegate.sshagent.AgentTrial
public class AgentTrial {

    static final String SSH = "C:\\Windows\\System32\\OpenSSH\\";
    static int failed;
    static volatile boolean allow = true;
    static volatile String seenCommandLine = "";

    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("agent-trial");
        var key = AgentKey.generate("trial key");
        var pub = dir.resolve("trial.pub");
        Files.writeString(pub, key.publicLine() + "\n");
        var pipe = "leasegate-agent-trial-" + ProcessHandle.current().pid();
        var agent = new AgentServer(() -> List.of(key), r -> { seenCommandLine = r.sshCommandLine(); return allow; });
        var listening = LeaseServer.open(dir, pipe);
        Thread.ofPlatform().daemon().start(() -> agent.serve(listening, "agent-trial"));
        var sock = "\\\\.\\pipe\\" + pipe;

        var listed = run(dir, sock, SSH + "ssh-add.exe", "-L");
        check("ssh-add lists the key the agent holds", listed.contains(key.publicLine().split(" ")[1]));

        var fp = run(dir, sock, SSH + "ssh-keygen.exe", "-l", "-f", pub.toString());
        check("our fingerprint is the one ssh-keygen prints", fp.contains(key.fingerprint()));

        var msg = dir.resolve("message.txt");
        Files.writeString(msg, "signed through the agent\n");
        run(dir, sock, SSH + "ssh-keygen.exe", "-Y", "sign", "-f", pub.toString(), "-n", "file", msg.toString());
        var sig = Path.of(msg + ".sig");
        check("ssh-keygen -Y sign with only the public key file made a signature", Files.exists(sig));
        check("the policy saw the signing program's own command line", seenCommandLine.contains("-Y"));
        var verified = runStdin(dir, sock, msg, SSH + "ssh-keygen.exe", "-Y", "check-novalidate", "-n", "file", "-s", sig.toString());
        check("ssh-keygen verifies the signature", verified.contains("Good"));

        allow = false;
        Files.deleteIfExists(sig);
        run(dir, sock, SSH + "ssh-keygen.exe", "-Y", "sign", "-f", pub.toString(), "-n", "file", msg.toString());
        check("with the policy saying no, nothing is signed", !Files.exists(sig));

        var made = dir.resolve("made");
        run(dir, sock, SSH + "ssh-keygen.exe", "-q", "-t", "ed25519", "-N", "", "-C", "made by ssh-keygen", "-f", made.toString());
        var imported = OpenSshKeyFile.parse(Files.readString(made));
        check("a key file made by ssh-keygen imports with the same fingerprint and comment",
            run(dir, sock, SSH + "ssh-keygen.exe", "-l", "-f", made + ".pub").contains(imported.fingerprint()) && imported.comment.equals("made by ssh-keygen"));
        check("...and its public line is the one in the .pub file", Files.readString(Path.of(made + ".pub")).trim().equals(imported.publicLine()));
        var locked = dir.resolve("locked");
        run(dir, sock, SSH + "ssh-keygen.exe", "-q", "-t", "ed25519", "-N", "Trialpass9", "-f", locked.toString());
        check("a passphrase-protected key file is refused with a clear message", refusesLocked(Files.readString(locked)));

        var back = AgentKey.fromStored(key.toStored());
        check("a key stored and read back is the same key", back.fingerprint().equals(key.fingerprint()) && back.comment.equals("trial key"));
        System.out.println(failed == 0 ? "ALL CHECKS PASSED" : failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }

    static boolean refusesLocked(String text) {
        try { OpenSshKeyFile.parse(text); return false; } catch (IllegalArgumentException e) { return e.getMessage().contains("passphrase"); }
    }

    static String run(Path dir, String sock, String... cmd) throws Exception { return runStdin(dir, sock, null, cmd); }

    static String runStdin(Path dir, String sock, Path stdin, String... cmd) throws Exception {
        var pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
        pb.environment().put("SSH_AUTH_SOCK", sock);
        if (stdin != null) pb.redirectInput(stdin.toFile());
        var p = pb.start();
        var out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        System.out.println("  $ " + String.join(" ", cmd).replace(dir.toString(), "<tmp>") + "\n    " + out.trim().replace("\n", "\n    "));
        return out;
    }

    static void check(String label, boolean ok) {
        if (!ok) failed++;
        System.out.println((ok ? "ok - " : "FAILED - ") + label);
    }
}
