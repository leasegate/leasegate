package leasegate.core.trial;

import java.util.List;

import leasegate.core.access.Handshake;
import leasegate.core.access.KeyedAppStore;
import leasegate.transport.LeaseKey;

import static leasegate.core.trial.Checks.check;

/// The first-frame parser and the key proof, as pure logic.
/// java -cp <classes> leasegate.core.trial.HandshakeTrial
public class HandshakeTrial {
    public static void main(String[] args) throws Exception {
        check("a frame without a gate object is no handshake", Handshake.extract("{\"id\":1,\"method\":\"Browser.getVersion\"}").isEmpty());
        check("garbage is no handshake either", Handshake.extract("not json").isEmpty());

        var h = Handshake.extract("{\"gate\":{\"v\":2,\"op\":\"connect\",\"app\":\"recorder\",\"items\":[\"repo-a\",\" \"],"
            + "\"minutes\":60}}").orElseThrow();
        check("op, app, minutes parsed", h.op().equals("connect") && h.app().equals("recorder") && h.minutes() == 60);
        check("the gate's own fields stay in body, blank entries dropped", Handshake.strings(h.body(), "items").equals(List.of("repo-a")));
        check("no publicKey means attested", !h.keyed());
        check("an absent list means not narrowed", Handshake.strings(Handshake.extract("{\"gate\":{\"op\":\"connect\"}}").orElseThrow().body(), "items") == null);
        check("profiles op parsed", Handshake.extract("{\"gate\":{\"v\":2,\"op\":\"profiles\"}}").orElseThrow().op().equals("profiles"));

        var dir = java.nio.file.Files.createTempDirectory("gatekey");
        var key = LeaseKey.loadOrCreate(dir.resolve("a.json"));
        var again = LeaseKey.loadOrCreate(dir.resolve("a.json"));
        check("a key reloads with the same public half", key.publicKey().equals(again.publicKey()));
        var nonce = KeyedAppStore.newChallenge();
        check("a signature verifies against its own key", KeyedAppStore.verify(key.publicKey(), nonce, again.sign(nonce)));
        var other = LeaseKey.loadOrCreate(dir.resolve("b.json"));
        check("another key's signature does not", !KeyedAppStore.verify(key.publicKey(), nonce, other.sign(nonce)));
        check("a signature does not verify a different challenge", !KeyedAppStore.verify(key.publicKey(), KeyedAppStore.newChallenge(), key.sign(nonce)));
        check("fingerprints are 16 hex digits", KeyedAppStore.fingerprint(key.publicKey()).matches("[0-9a-f]{16}"));
        System.exit(Checks.summary());
    }
}
