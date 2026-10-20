package leasegate.core.access;

import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import leasegate.core.win.PeerIdentity;

/// Keyed approvals, persisted in the gate's own `keyed-apps.json` because these apps reconnect
/// after a restart with nobody at the machine. Holds public keys only, so the file itself grants
/// nothing to whoever reads it. How long an approval may last is the gate's rule
/// (`LeaseRules.keyedMaxMinutes`), by how narrowly it is scoped.
public final class KeyedAppStore<S extends Scope<S>> {

    static final SecureRandom RANDOM = new SecureRandom();

    final Path storePath;
    final LeaseRules<S> rules;
    final ObjectMapper mapper = new ObjectMapper();
    List<KeyedApp<S>> apps;

    public KeyedAppStore(Path storePath, LeaseRules<S> rules) {
        this.storePath = storePath;
        this.rules = rules;
        apps = new ArrayList<>(KeyedAppJson.load(mapper, storePath, rules));
    }

    public synchronized List<KeyedApp<S>> all() { return List.copyOf(apps); }

    public synchronized Optional<KeyedApp<S>> byKey(String publicKey) {
        return apps.stream().filter(a -> a.publicKey().equals(publicKey)).findFirst();
    }

    public synchronized boolean isValid(String id) {
        var now = Instant.now();
        return apps.stream().anyMatch(a -> a.id().equals(id) && !a.expired(now));
    }

    /// Records a human's approval for this key, replacing any earlier one for the same key.
    public synchronized KeyedApp<S> approve(String app, String publicKey, S scope, int minutes, PeerIdentity peer) {
        var now = Instant.now();
        var previous = byKey(publicKey);
        var entry = new KeyedApp<>(previous.map(KeyedApp::id).orElse(UUID.randomUUID().toString()), app, publicKey,
            fingerprint(publicKey), rules.stored(scope), now,
            now.plusSeconds(Math.min(minutes, rules.keyedMaxMinutes(scope)) * 60L), peer.imagePath(), peer.pid(), false);
        apps.removeIf(a -> a.publicKey().equals(publicKey));
        apps.add(entry);
        save();
        return entry;
    }

    /// Notes who presented the key this time, flagging a change of executable.
    public synchronized KeyedApp<S> seen(KeyedApp<S> a, PeerIdentity peer) {
        var changed = a.lastSeenImagePath() != null && !a.lastSeenImagePath().equalsIgnoreCase(peer.imagePath());
        var updated = new KeyedApp<>(a.id(), a.app(), a.publicKey(), a.fingerprint(), a.scope(), a.approvedAt(), a.expiresAt(),
            peer.imagePath(), peer.pid(), a.flagged() || changed);
        apps.replaceAll(x -> x.id().equals(a.id()) ? updated : x);
        save();
        return updated;
    }

    public synchronized void revoke(String id) { apps.removeIf(a -> a.id().equals(id)); save(); }

    public synchronized void clearFlag(String id) {
        apps.replaceAll(a -> !a.id().equals(id) ? a : new KeyedApp<>(a.id(), a.app(), a.publicKey(), a.fingerprint(),
            a.scope(), a.approvedAt(), a.expiresAt(), a.lastSeenImagePath(), a.lastSeenPid(), false));
        save();
    }

    public static String newChallenge() {
        var b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    public static boolean verify(String publicKeyB64, String challengeB64, String signatureB64) {
        try {
            var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)));
            var s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(Base64.getDecoder().decode(challengeB64));
            return s.verify(Base64.getDecoder().decode(signatureB64));
        } catch (Exception e) { return false; }
    }

    /// Short, human-comparable identity of a key: first 16 hex digits of its SHA-256.
    public static String fingerprint(String publicKeyB64) {
        try {
            var d = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(publicKeyB64));
            return HexFormat.of().formatHex(d).substring(0, 16);
        } catch (Exception e) { return "invalid-key"; }
    }

    void save() { KeyedAppJson.save(mapper, storePath, apps, rules); }
}
