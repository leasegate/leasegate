package leasegate.core.trial;

import leasegate.transport.Links;

/// Companion to ConcurrentReadWriteTrial: sends message 1, reads the reply, then deliberately
/// never sends message 2, leaving the server's second readFrame() pending on purpose.
/// java --enable-native-access=ALL-UNNAMED -cp <classes> leasegate.core.trial.ConcurrentReadWriteClientTrial
public class ConcurrentReadWriteClientTrial {
    public static void main(String[] args) throws Exception {
        try (var client = Links.open("trial", TestScope.RUN_DIR, "leasegate-concurrent-trial")) {
            client.writeFrame("{\"hello\":\"message 1\"}");
            System.out.println("sent message 1, waiting for reply...");
            System.out.println("reply: " + client.readFrame());
            System.out.println("done -- deliberately not sending message 2");
            Thread.sleep(15000);
        }
    }
}
