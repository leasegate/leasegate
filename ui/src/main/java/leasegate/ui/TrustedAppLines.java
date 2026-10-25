package leasegate.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;

import leasegate.core.trusted.*;
import leasegate.core.trusted.TrustedAppApproval.Request;

/// The code part of the trusted-app window: what was hashed, what the GitHub check found, and, when
/// the code changed since it was trusted, the old and new hashes side by side.
final class TrustedAppLines {

    static List<Node> of(Request<?> r) {
        var out = new ArrayList<Node>();
        var c = r.code();
        switch (r.check()) {
            case GitHubCheck.Verified v -> out.add(ApprovalParts.small("Downloaded from GitHub just now and matched byte for byte."
                + " What " + v.org() + " publishes is its own responsibility; approving it is yours.", ApprovalParts.GREY));
            case GitHubCheck.NotVerified n -> out.add(ApprovalParts.small(r.declared().assets().isEmpty()
                ? "No GitHub release was declared, so only the local files' hashes are known."
                : "GitHub check: " + n.reason() + ". Treat it as a dev build.", ApprovalParts.AMBER));
        }
        if (c.jar() == null) out.add(ApprovalParts.small("A native exe: only the exe file itself is hashed, not files it loads beside it.", ApprovalParts.GREY));
        out.add(TrustedParts.mono("exe  " + c.exe() + "\n     sha256 " + c.exeSha256()));
        if (c.jar() != null) out.add(TrustedParts.mono("jar  " + c.jar() + "\n     sha256 " + c.jarSha256() + (c.jarSha256Pinned() ? "  (pinned by the exe's own config)" : "")));
        out.add(ApprovalParts.small("pid " + c.pid() + " · started " + TrustedParts.when(c.start()), ApprovalParts.GREY));
        r.previous().ifPresent(p -> {
            if (!r.codeChanged()) { out.add(ApprovalParts.small("Trusted since " + TrustedParts.when(p.trustedAt()) + ", same code.", ApprovalParts.GREEN)); return; }
            out.add(ApprovalParts.line("Its code changed since it was trusted on " + TrustedParts.when(p.trustedAt()) + ":", ApprovalParts.RED));
            out.add(TrustedParts.mono("exe  was " + p.exeSha256() + "\n     now " + c.exeSha256()));
            if (p.jarSha256() != null || c.jarSha256() != null)
                out.add(TrustedParts.mono("jar  was " + p.jarSha256() + "\n     now " + c.jarSha256()));
        });
        return out;
    }

    private TrustedAppLines() {}
}
