package leasegate.sshagent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import leasegate.vault.Vault;

/// A gate's SSH keys, kept in its vault as `ssh.<id>`, each with its public half written to
/// `<publicDir>/<id>.pub` -- the file ssh is pointed at with `-i` (or `IdentityFile`) so that it asks
/// the agent for exactly that key. The private half is only ever in the vault and in memory.
public final class VaultKeys {

    final Vault vault;
    final Path publicDir;

    public VaultKeys(Vault vault, Path publicDir) {
        this.vault = vault;
        this.publicDir = publicDir;
    }

    static String name(String id) { return "ssh." + id; }

    public Optional<AgentKey> get(String id) { return vault.get(name(id)).map(AgentKey::fromStored); }

    public List<OwnedKey> all() {
        var out = new ArrayList<OwnedKey>();
        for (var n : vault.names()) if (n.startsWith("ssh.")) vault.get(n).map(AgentKey::fromStored).ifPresent(k -> out.add(new OwnedKey(n.substring(4), k)));
        return out;
    }

    public List<AgentKey> keys() { return all().stream().map(OwnedKey::key).toList(); }

    /// Whose key this is, by fingerprint.
    public Optional<String> owner(AgentKey k) {
        return all().stream().filter(o -> o.key().fingerprint().equals(k.fingerprint())).map(OwnedKey::id).findFirst();
    }

    public void put(String id, AgentKey k) {
        vault.put(name(id), k.toStored());
        writePublic(id, k);
    }

    public Path publicFile(String id) { return publicDir.resolve(id + ".pub"); }

    public void writeAllPublic() { all().forEach(o -> writePublic(o.id(), o.key())); }

    void writePublic(String id, AgentKey k) {
        try {
            Files.createDirectories(publicDir);
            Files.writeString(publicFile(id), k.publicLine() + "\n");
        } catch (Exception e) { throw new IllegalStateException("cannot write " + publicFile(id) + ": " + e.getMessage()); }
    }
}
