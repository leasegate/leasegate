package leasegate.sshagent;

/// An SSH key and the name it is kept under: an account, a server, whatever the gate calls it.
public record OwnedKey(String id, AgentKey key) {}
