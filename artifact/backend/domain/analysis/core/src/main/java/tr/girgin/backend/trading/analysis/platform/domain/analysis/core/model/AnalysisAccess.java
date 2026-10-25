package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.util.Arrays;
import java.util.List;
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
        return allUsers || isOwner(analysis) || analysis.visibility().sharedWithUsers();
    }

    /** Stop, run again and read the log. */
    public boolean canManage(Analysis analysis) {
        return allUsers || isOwner(analysis);
    }

    /**
     * Whether the change is allowed to this caller; whether the status allows it is for the use case to
     * check. An admin only narrows what someone else shared, never widens it to the internet.
     */
    public boolean canChangeTo(Analysis analysis, Visibility target) {
        Visibility current = analysis.visibility();
        if (current == target) {
            return false;
        }
        boolean ownerWithShare = share && isOwner(analysis);
        boolean allUsersWithShare = share && allUsers;
        return switch (target) {
            case PRIVATE -> isOwner(analysis) || allUsersWithShare;
            case PUBLIC -> ownerWithShare || current == Visibility.PARTIAL && allUsersWithShare;
            case PARTIAL -> ownerWithShare;
        };
    }

    public List<Visibility> allowedVisibilities(Analysis analysis) {
        return Arrays.stream(Visibility.values())
                .filter(target -> canChangeTo(analysis, target))
                .toList();
    }

    public boolean isOwner(Analysis analysis) {
        return user.equals(analysis.owner());
    }
}
