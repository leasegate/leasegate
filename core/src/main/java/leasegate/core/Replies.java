package leasegate.core;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/// The `{"gate":{..}}` frames a gate sends a client before anything else flows. Every refusal
/// carries a hint saying what to change; every approval says until when, and can say how a
/// narrower request would have fared -- the nudge towards narrow requests lives in the protocol.
public final class Replies {

    public static final ObjectMapper M = new ObjectMapper();

    public static String denied(String error, String hint) {
        return wrap(M.createObjectNode().put("ok", false).put("error", error).put("hint", hint));
    }

    public static String challenge(String nonce) { return wrap(M.createObjectNode().put("challenge", nonce)); }

    /// `fields` carries the gate's own description of what was approved (its scope), merged in.
    public static String approved(String mode, Instant expiresAt, ObjectNode fields, String hint) {
        var g = M.createObjectNode().put("ok", true).put("mode", mode).put("expiresAt", expiresAt.toString());
        if (fields != null) g.setAll(fields);
        if (hint != null) g.put("hint", hint);
        return wrap(g);
    }

    public static String wrap(ObjectNode body) { return M.createObjectNode().set("gate", body).toString(); }

    private Replies() {}
}
