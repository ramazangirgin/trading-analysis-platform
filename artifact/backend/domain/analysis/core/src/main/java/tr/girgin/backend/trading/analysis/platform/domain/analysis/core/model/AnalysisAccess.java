package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.util.Objects;

/**
 * Who asks, whether they may see the analyses of every user, and whether they hold {@code analysis:share}.
 * The rules on who may see, manage and publish an analysis are written here and nowhere else.
 */
public record AnalysisAccess(OwnerId user, boolean allUsers, boolean share) {

    public AnalysisAccess {
        Objects.requireNonNull(user, "user");
    }

    public boolean canSee(Analysis analysis) {
        return allUsers || isOwner(analysis) || analysis.visibility() == Visibility.PUBLIC;
    }

    /** Stop, run again and read the log. */
    public boolean canManage(Analysis analysis) {
        return allUsers || isOwner(analysis);
    }

    /** Whether the status allows it is for the use case to check. */
    public boolean canMakePublic(Analysis analysis) {
        return share && isOwner(analysis);
    }

    public boolean canMakePrivate(Analysis analysis) {
        boolean isAdminWithShare = allUsers && share;
        return analysis.visibility() == Visibility.PUBLIC && (isOwner(analysis) || isAdminWithShare);
    }

    public boolean canChangeVisibility(Analysis analysis) {
        return analysis.visibility() == Visibility.PRIVATE ? canMakePublic(analysis) : canMakePrivate(analysis);
    }

    public boolean isOwner(Analysis analysis) {
        return user.equals(analysis.owner());
    }
}
