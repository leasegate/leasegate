package leasegate.core.trusted;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/// Resources of a Windows exe, read from bytes already in memory: the bytes the gate hashed are the
/// bytes it reads jr's config from, so the two cannot disagree. Plain PE parsing, no loader, every
/// offset bounds-checked; a file that is not a well-formed PE gives empty answers, never an error.
public final class PeImage {

    public static final int RT_ICON = 3, RT_RCDATA = 10;

    /// One directory entry: its id (-1 when named), its target, its name's offset (-1 when an id),
    /// and whether the target is another directory.
    record Entry(int id, int target, int nameAt, boolean directory) {}

    final ByteBuffer b;
    final List<int[]> sections = new ArrayList<>();
    int resourceRva = -1;

    PeImage(byte[] bytes) {
        b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        try { parseHeaders(); } catch (RuntimeException e) { resourceRva = -1; }
    }

    public static PeImage of(byte[] bytes) { return new PeImage(bytes); }

    /// One resource by type and string name (jr's config is RCDATA "JRC"), any language.
    public Optional<byte[]> named(int type, String name) {
        try { return dirOf(type).flatMap(d -> entries(d).stream().filter(e -> e.nameAt() >= 0 && name.equalsIgnoreCase(stringAt(e.nameAt()))).findFirst()).flatMap(this::leaf); }
        catch (RuntimeException e) { return Optional.empty(); }
    }

    /// Every resource of a type, whatever its name.
    public List<byte[]> all(int type) {
        var out = new ArrayList<byte[]>();
        try { dirOf(type).ifPresent(d -> entries(d).forEach(e -> leaf(e).ifPresent(out::add))); }
        catch (RuntimeException ignored) {}
        return out;
    }

    /// The largest PNG-compressed icon image, as jr stamps them; empty when there is none.
    public Optional<byte[]> largestPngIcon() {
        return all(RT_ICON).stream().filter(PeImage::png).max(Comparator.comparingInt(x -> x.length));
    }

    static boolean png(byte[] x) { return x.length > 8 && (x[0] & 0xFF) == 0x89 && x[1] == 'P' && x[2] == 'N' && x[3] == 'G'; }

    void parseHeaders() {
        if (b.getShort(0) != 0x5A4D) return;
        var pe = b.getInt(0x3C);
        if (b.getInt(pe) != 0x00004550) return;
        var count = Short.toUnsignedInt(b.getShort(pe + 6));
        var optSize = Short.toUnsignedInt(b.getShort(pe + 20));
        var opt = pe + 24;
        var dirs = opt + (Short.toUnsignedInt(b.getShort(opt)) == 0x20B ? 112 : 96);
        for (var i = 0; i < count; i++) {
            var s = opt + optSize + i * 40;
            sections.add(new int[] {b.getInt(s + 12), Math.max(b.getInt(s + 8), b.getInt(s + 16)), b.getInt(s + 20)});
        }
        var rva = b.getInt(dirs + 2 * 8);
        resourceRva = rva == 0 ? -1 : rva;
    }

    int offset(int rva) {
        for (var s : sections)
            if (Integer.compareUnsigned(rva, s[0]) >= 0 && Integer.compareUnsigned(rva, s[0] + s[1]) < 0) return rva - s[0] + s[2];
        throw new IndexOutOfBoundsException("rva " + rva);
    }

    int base() { return offset(resourceRva); }

    Optional<Integer> dirOf(int type) {
        if (resourceRva == -1) return Optional.empty();
        return entries(base()).stream().filter(e -> e.id() == type && e.directory()).map(Entry::target).findFirst();
    }

    List<Entry> entries(int dir) {
        var count = Short.toUnsignedInt(b.getShort(dir + 12)) + Short.toUnsignedInt(b.getShort(dir + 14));
        var out = new ArrayList<Entry>();
        for (var i = 0; i < count; i++) {
            var at = dir + 16 + i * 8;
            var name = b.getInt(at);
            var ptr = b.getInt(at + 4);
            out.add(new Entry(name < 0 ? -1 : name, base() + (ptr & 0x7FFFFFFF), name < 0 ? base() + (name & 0x7FFFFFFF) : -1, ptr < 0));
        }
        return out;
    }

    String stringAt(int at) {
        var chars = new byte[Short.toUnsignedInt(b.getShort(at)) * 2];
        b.get(at + 2, chars);
        return new String(chars, StandardCharsets.UTF_16LE);
    }

    /// Down the first entry of each level (name, then language) to the data, and its bytes.
    Optional<byte[]> leaf(Entry e) {
        for (var depth = 0; e.directory(); depth++) {
            var es = entries(e.target());
            if (es.isEmpty() || depth > 3) return Optional.empty();
            e = es.getFirst();
        }
        var size = b.getInt(e.target() + 4);
        if (size < 0 || size > (64 << 20)) return Optional.empty();
        var out = new byte[size];
        b.get(offset(b.getInt(e.target())), out);
        return Optional.of(out);
    }
}
