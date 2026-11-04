package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.AnalysisRights;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisRightsPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

@Service
class AnalysisAccessService implements GetAnalysisRightsPort {

    @Override
    public AnalysisRights rights(Analysis analysis, AnalysisAccess access) {
        List<Visibility> allowedVisibilities = allowedVisibilities(analysis, access);
        return new AnalysisRights(canManage(analysis, access), !allowedVisibilities.isEmpty(), allowedVisibilities);
    }

    boolean canSee(Analysis analysis, AnalysisAccess access) {
        return access.allUsers() || isOwner(analysis, access) || sharedWithUsers(analysis.visibility());
    }

    /** Stop, run again and read the log. */
    boolean canManage(Analysis analysis, AnalysisAccess access) {
        return access.allUsers() || isOwner(analysis, access);
    }

    // Whether the status allows the change is for the use case to check. An admin only narrows what someone
    // else shared, never widens it to the internet.
    boolean canChangeTo(Analysis analysis, AnalysisAccess access, Visibility target) {
        Visibility current = analysis.visibility();
        if (current == target) {
            return false;
        }
        boolean ownerWithShare = access.share() && isOwner(analysis, access);
        boolean allUsersWithShare = access.share() && access.allUsers();
        return switch (target) {
            case PRIVATE -> isOwner(analysis, access) || allUsersWithShare;
            case PUBLIC -> ownerWithShare || current == Visibility.PARTIAL && allUsersWithShare;
            case PARTIAL -> ownerWithShare;
        };
    }

    List<Visibility> allowedVisibilities(Analysis analysis, AnalysisAccess access) {
        return Arrays.stream(Visibility.values())
                .filter(target -> canChangeTo(analysis, access, target))
                .toList();
    }

    boolean isOwner(Analysis analysis, AnalysisAccess access) {
        return access.user().equals(analysis.owner());
    }

    boolean sharedWithUsers(Visibility visibility) {
        return visibility != Visibility.PRIVATE;
    }
}
