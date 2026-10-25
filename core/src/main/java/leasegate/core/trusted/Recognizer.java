package leasegate.core.trusted;

import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import leasegate.core.ProcInfo;
import leasegate.core.trusted.Recognition.*;
import leasegate.core.win.RemoteProcessInfo;
import leasegate.transport.LeasePlatform;

/// Names the code a calling process runs: its exe, and for a jr app the jar its own embedded config
/// launches. Done once per process start (pid + start time), so a pid Windows reuses for another
/// program is never mistaken for the first. The app's declaration is then checked against what was
/// found, on every call, and believed in nothing.
public final class Recognizer {

    static final java.util.Set<String> INJECTING = java.util.Set.of("java_tool_options", "jdk_java_options", "_java_options");

    final Map<String, Recognition> cache = new ConcurrentHashMap<>();

    public Recognition recognize(long pid, Declaration d) {
        return check(cache.computeIfAbsent(key(pid), k -> inspect(pid)), d);
    }

    /// The cached answer for a process, if it was recognized before (the status call uses it).
    public Recognition recognize(long pid) { return cache.computeIfAbsent(key(pid), k -> inspect(pid)); }

    static String key(long pid) {
        return pid + "@" + ProcessHandle.of(pid).flatMap(p -> p.info().startInstant()).map(Instant::toEpochMilli).orElse(-1L);
    }

    public void forgetDead() { cache.keySet().removeIf(k -> !k.equals(key(Long.parseLong(k.substring(0, k.indexOf('@')))))); }

    static Recognition inspect(long pid) {
        if (!LeasePlatform.WINDOWS)
            return new Refused(pid, "trusted apps are not available on this system yet",
                "the calling process cannot yet be tied to its exe reliably here, so long approvals are refused");
        var handle = ProcessHandle.of(pid);
        var start = handle.flatMap(p -> p.info().startInstant());
        var image = handle.flatMap(p -> p.info().command());
        if (start.isEmpty() || image.isEmpty()) return new Refused(pid, "the process could not be identified", "it may have exited");
        var exe = Path.of(image.get());
        var name = exe.getFileName().toString();
        if (Runtimes.generic(name))
            return new Refused(pid, name + " is a shared runtime: its hash says nothing about the program it runs",
                "run the app as its own exe; a Java app as a jr exe with jvm.mode dll and its jar in jar.sources");
        byte[] bytes;
        try { bytes = Files.readAllBytes(exe); }
        catch (Exception e) { return new Refused(pid, "cannot read " + exe, e.getMessage()); }
        return jr(pid, start.get(), exe, bytes);
    }

    static Recognition jr(long pid, Instant start, Path exe, byte[] bytes) {
        var pe = PeImage.of(bytes);
        var icon = pe.largestPngIcon().orElse(null);
        var exeHash = Hashes.sha256(bytes);
        var raw = pe.named(PeImage.RT_RCDATA, "JRC");
        if (raw.isEmpty()) return Runtimes.looksLikeJr(bytes)
            ? new Refused(pid, exe.getFileName() + " is a jr launcher with no config of its own", "it runs whatever its command line names; bake a jrc-json into it")
            : new Recognized(pid, start, exe.toString(), exeHash, null, null, false, icon);
        var config = JrConfig.parse(raw.get());
        if (config.isEmpty()) return new Refused(pid, "the jr config inside " + exe.getFileName() + " cannot be read", "it must be jrc-json");
        var c = config.get();
        if (c.javaArgs()) return new Refused(pid, "its jr config launches a classpath, not one jar", "use jar.sources, not jvm.javaArgs");
        if ("exe".equalsIgnoreCase(c.jvmMode())) return new Refused(pid, "its jr config starts Java as a child process", "use jvm.mode dll, so the app's own exe is what connects");
        var process = launch(pid);
        if (process != null) return process;
        var jar = c.jar(exe.getParent());
        if (jar.isEmpty()) return new Refused(pid, "the jar named in its jr config is not on this machine", "run the app once so jr fetches it");
        var jarHash = Hashes.sha256(jar.get());
        var pinned = c.sha256() != null;
        if (pinned && !c.sha256().equalsIgnoreCase(jarHash))
            return new Refused(pid, "its jar does not match the sha256 baked into the exe", "the jar was changed after it was downloaded");
        return new Recognized(pid, start, exe.toString(), exeHash, jar.get().toString(), jarHash, pinned, icon);
    }

    /// Ways around a jr config that the exe's hash cannot see: `-Xjr:` options on the command line,
    /// and Java options in the environment (a `-javaagent` loads any code).
    static Refused launch(long pid) {
        var cmd = ProcInfo.commandLine(pid);
        if (cmd.isEmpty()) return new Refused(pid, "its command line cannot be read", "a long approval needs it checked");
        if (cmd.get().contains("-Xjr:")) return new Refused(pid, "it was started with -Xjr: options, which can change what it runs", "start it with no -Xjr: options");
        var env = RemoteProcessInfo.environment(pid);
        if (env.isEmpty()) return new Refused(pid, "its environment cannot be read", "a long approval needs it checked");
        for (var name : env.get().keySet())
            if (INJECTING.contains(name.toLowerCase())) return new Refused(pid, "it runs with " + name + " set, which can load other code into it", "start it without " + name);
        return null;
    }

    /// What the app declared must be what was found.
    static Recognition check(Recognition r, Declaration d) {
        if (!(r instanceof Recognized g) || d == null) return r;
        if (d.exe() != null && !same(d.exe(), g.exe())) return new Refused(g.pid(), "it says it is " + d.exe() + " but the process is " + g.exe(), "declare the exe it really runs");
        if (d.jar() != null && (g.jar() == null || !same(d.jar(), g.jar())))
            return new Refused(g.pid(), "it says it runs " + d.jar() + " but its exe runs " + (g.jar() == null ? "no jar" : g.jar()), "declare the jar its jr config names");
        return r;
    }

    static boolean same(String a, String b) {
        return Path.of(a).toAbsolutePath().normalize().toString().equalsIgnoreCase(Path.of(b).toAbsolutePath().normalize().toString());
    }
}
