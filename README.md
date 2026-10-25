# leasegate

**Nothing on your machine holds standing power. Every process gets a lease.**

A developer's or administrator's workstation holds a lot of power: SSH keys, API tokens, browser sessions, cloud credentials. They usually sit in plain files and environment variables, readable by every program the user runs. That was tolerable while the only thing running commands was the human. Now AI agents and automated CLI tools run on the same machine with all of it in reach, and a single prompt injection can turn them.

leasegate is a Java library for building **gates**: small tray applications that hold a credential (or the only link to something powerful) and hand out access to it on these terms:

- **The kernel says who is asking.** A client connects over a named pipe (Windows) or a Unix domain socket (macOS) that only the current user can open, and the gate asks the operating system for the connecting process id. Nothing the client says about itself is trusted.
- **A human sees the whole process chain before approving.** The approval window shows the requesting program, its full command line and working directory, and every ancestor up to the desktop: `git.exe` under `bash.exe` under `claude.exe` under a terminal.
- **The approval is anchored, scoped and time-boxed.** The human picks how far up the chain it reaches. A broader anchor earns a shorter approval, a narrower scope earns a longer one, and the approval ends early the moment its anchor process exits. Desktop shells and terminal hosts can never be chosen as anchors.
- **Nothing is permanent.** Grants live in memory and expire; live connections are cut when they do; a warning appears five minutes before. Apps started at logon can hold a keyed approval instead, proved by signing a fresh challenge, never by a stored bearer secret.
- **A passphrase stands behind the widest requests**, and a time-limited "blanket mode" exists for testing, entered by typing the passphrase twice.

Industry words for the idea are *just-in-time access* and *zero standing privileges*. leasegate brings them to the personal machine, aimed at one's own tools.

## Modules

- `leasegate-transport` — the client side: framed and raw links over the pipe or socket, keyed-app identity (Ed25519), and the trusted-app client. No UI, one dependency (Jackson).
- `leasegate-core` — the server side: kernel peer identity, the user-only pipe, the process chain (including through MSYS/Cygwin fork emulation), anchor classification and duration caps, grants, keyed apps, trust, the passphrase, and `LeaseEngine`, which finds a covering grant or asks.
- `leasegate-ui` — JavaFX: the approval, trust and access windows, passphrase dialogs, expiry warning, tray shell, and vault unlock with a one-time recovery key.
- `leasegate-vault` — secrets at rest: AES-256-GCM under a random data key, sealed twice (PBKDF2 of the passphrase; a recovery key shown once).
- `leasegate-sshagent` — an ssh agent served by the gate. Keys are made inside it or imported once; the private key never leaves the process, and every signature goes through the gate's policy. Works with Windows' built-in OpenSSH (`SSH_AUTH_SOCK=\\.\pipe\<name>`).

## Writing a gate

A gate supplies one thing the library cannot know: what a request reaches. That is a `Scope` (whether one fits inside another, and how narrow it is) and `LeaseRules` (the product name, the extra cap a scope earns, how it is described in the windows, how it is stored). Everything else is shared.

```java
var rules = new MyRules();
var gate = new PassphraseGate(home.resolve("passphrase.json"));
PassphraseUnlockDialog.unlockOrSetup(gate, rules.product());
var engine = new LeaseEngine<>(rules, new BlanketModeApproval<>(new FxApproval<>(gate, rules), gate),
    new KeyedAppStore<>(home.resolve("keyed-apps.json"), rules)).startSweep();
new TrayShell<>(stage, engine, gate, GateIcon.image(), () -> "running").install();
LeaseServer.serve(LeaseServer.open(home.resolve("run"), "mygate"), "mygate", accepted -> {
    // read the request, then: engine.attest(accepted.peer(), chain, scope, label)
});
```

## Trusted apps: long approvals for resident clients

A tray app that talks to a gate all day should not have to be approved every hour, and should not be given a key that works for any program. A trusted app gets a long approval (8 hours offered, at most a day) that is bound to one run of the app (its process id and start time) and to the exact code it runs: the SHA-256 of its exe, and for a Java app the SHA-256 of its jar. Windows only for now.

- **Shared runtimes are refused.** `java.exe`, `node.exe`, `python.exe`, shells, script hosts and the like say nothing about the program they run, so they can never hold one. A Java app runs as its own [jr](https://github.com/jarrunner/jr) exe in `jvm.mode` `dll`. The gate reads the exe's embedded config from the same bytes it hashed, finds the jar that config launches, and hashes that too. It refuses a jr launcher with no config of its own, a `-Xjr:` option on the command line, and `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS` or `_JAVA_OPTIONS` in the app's environment, because each of those can change what runs without changing the exe.
- **Two levels, shown in the approval window.** A dev build is checked locally, by hash only. A verified app has also declared its GitHub project and release assets: the gate downloads them itself over HTTPS and they must match the local files byte for byte. The window then shows a green tick with the organisation, repository and release tag. The tick never approves anything. The human still types the passphrase.
- **Code changes are noticed.** A changed exe or jar makes the app an ordinary client again. Trusting it again shows the old and new hashes side by side.
- **Delegated unlock.** An app with an open approval can ask the gate to show its passphrase prompt on the app's behalf. The app keeps no password and never sees the passphrase. It only receives an answer saying the right one was typed.
- **Signed replies.** Every reply is signed with the gate's Ed25519 key over the app's fresh nonce, the caller's pid and start time, and the hashes. The app checks the reply against the gate's `trusted-apps.pub`. A process squatting the gate's address, or replaying an old answer, gets nowhere.
- **Status, and the app's side of the contract.** `status` says whether this run's approval is open and until when. If it is not open, it says why: not trusted, code changed, expired, ended, or refused. The app must lock its own UI whenever the answer is not `open`, because the gate cannot enforce that.
- **The host locks on its own clock.** An open long approval keeps working through the host's idle lock, with only the material it needs (`MaterialSource`) held in memory. That material is zero-filled when the approval ends. When the user locks the host on purpose, the host calls `LongApprovalsOnLock.ask`, which lists the open approvals so one key can end them all.

```java
var trusted = FxTrustedApps.install(new TrustedApps<>(home, rules, gate, audit::log), rules, gate, brand)
    .material((app, scope, passphrase) -> new HeldMaterial(Map.of("token", tokenFor(scope, passphrase))))
    .start();
// in the host's loopback handler, with the pid the kernel names for the connection:
reply(trusted.handle(peerPid, requestJson));
// on each later request from that process:
trusted.approvalFor(peerPid).filter(a -> a.scope().covers(requested)).ifPresentOrElse(..., ...);
```

On the app's side, `TrustedClient` (in `leasegate-transport`) builds the requests and accepts only genuine replies.

## Status

Windows is the primary platform and is exercised daily. The macOS socket server exists and has run on Apple silicon; it is less tested. Linux peer credentials are not implemented.

What a gate running as the same user cannot stop: another process of that user that is determined to read the gate's memory. leasegate stops accidents, over-eager agents, credentials leaking into transcripts and backups, and anything that would otherwise just read a file. Running the gate under a separate account is the next step up.

## Building

JDK 25, Maven: `mvn install`. The trials are plain `main` programs under `src/test/java`, run with `mvn exec:java -Dexec.mainClass=<class> -Dexec.classpathScope=test`.

## License

Apache License 2.0.
