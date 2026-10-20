package leasegate.transport;

import java.nio.file.Path;

/// Where a gate listens, per platform. Windows: the named pipe `\.\pipe\<name>`. Elsewhere: a
/// Unix domain socket `<name>.sock` in a run directory only this user can enter -- the same "this
/// user and nobody else" that the pipe's DACL gives on Windows. Each gate names its own run
/// directory (`~/<org>/<app>/run`).
public final class LeasePlatform {

    public static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().startsWith("windows");

    public static Path socketPath(Path runDir, String name) { return runDir.resolve(name + ".sock"); }

    public static String pipePath(String name) { return "\\\\.\\pipe\\" + name; }

    public static String describe(Path runDir, String name) { return WINDOWS ? pipePath(name) : socketPath(runDir, name).toString(); }

    private LeasePlatform() {}
}
