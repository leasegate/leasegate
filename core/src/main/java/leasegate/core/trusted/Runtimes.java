package leasegate.core.trusted;

import java.util.Set;

/// Programs whose hash says nothing about what they run, so no long approval may ever be bound to
/// one: the program is whatever their command line, script or classpath says. By file name, lower
/// case. A bare jr launcher is caught separately (it carries no config of its own).
public final class Runtimes {

    public static final Set<String> GENERIC = Set.of(
        "java.exe", "javaw.exe", "jshell.exe", "jr.exe", "jbang.exe",
        "node.exe", "bun.exe", "deno.exe", "python.exe", "pythonw.exe", "py.exe", "pyw.exe",
        "ruby.exe", "perl.exe", "php.exe", "dotnet.exe", "lua.exe",
        "cmd.exe", "powershell.exe", "pwsh.exe", "bash.exe", "sh.exe", "wsl.exe",
        "wscript.exe", "cscript.exe", "mshta.exe", "rundll32.exe", "regsvr32.exe", "msiexec.exe");

    /// The text every jr launcher carries for its own `-Xjr:` options; jr's strings are UTF-16
    /// (measured on jr 1.4), the ASCII form is checked too in case a build stores them that way.
    static final byte[][] JR_MARKERS = {"Xjr:".getBytes(java.nio.charset.StandardCharsets.UTF_16LE),
        "Xjr:".getBytes(java.nio.charset.StandardCharsets.US_ASCII)};

    public static boolean generic(String imageName) { return GENERIC.contains(imageName.toLowerCase()); }

    static boolean looksLikeJr(byte[] exe) {
        for (var m : JR_MARKERS) if (indexOf(exe, m) >= 0) return true;
        return false;
    }

    static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (var i = 0; i + needle.length <= hay.length; i++) {
            for (var j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    private Runtimes() {}
}
