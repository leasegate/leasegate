package leasegate.core.trial;

import leasegate.transport.Links;

/// Companion to CrossThreadWriteTrial: sends one message, then waits to read the reply.
/// java --enable-native-access=ALL-UNNAMED -cp <classes> leasegate.core.trial.CrossThreadReadClientTrial
public class CrossThreadReadClientTrial {
    public static void main(String[] args) throws Exception {
        try (var client = Links.open("trial", TestScope.RUN_DIR, "leasegate-crossthread-trial")) {
            client.writeFrame("{\"hello\":\"world\"}");
            System.out.println("sent, waiting for reply...");
            System.out.println("reply: " + client.readFrame());
        }
    }
}
