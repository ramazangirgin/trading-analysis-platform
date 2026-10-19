package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.util.Objects;

/** Who asks, and whether they may see the analyses of every user. */
public record AnalysisAccess(OwnerId user, boolean allUsers) {

    public AnalysisAccess {
        Objects.requireNonNull(user, "user");
    }

    public boolean canSee(Analysis analysis) {
        return allUsers || user.equals(analysis.owner());
    }
}
