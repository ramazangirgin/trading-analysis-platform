package tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.inbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisRightsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.model.AnalysisOwner;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.model.AnalysisView;

/** Adds the caller's rights and the owner's username to analyses. */
@Service
public class ViewAnalysesUseCase {

    private final GetAnalysisRightsUseCase getAnalysisRights;
    private final UserDirectoryUseCase userDirectory;

    ViewAnalysesUseCase(GetAnalysisRightsUseCase getAnalysisRights, UserDirectoryUseCase userDirectory) {
        this.getAnalysisRights = getAnalysisRights;
        this.userDirectory = userDirectory;
    }

    public AnalysisView view(Analysis analysis, AnalysisAccess access) {
        return view(List.of(analysis), access).getFirst();
    }

    /** Keeps the order of {@code analyses}; looks the usernames up once for the whole list. */
    public List<AnalysisView> view(List<Analysis> analyses, AnalysisAccess access) {
        Set<UserId> ids = analyses.stream()
                .map(Analysis::owner)
                .filter(Objects::nonNull)
                .map(owner -> new UserId(owner.value()))
                .collect(Collectors.toSet());
        Map<UserId, Username> usernames = userDirectory.usernames(ids);
        return analyses.stream()
                .map(analysis -> new AnalysisView(
                        analysis, owner(analysis.owner(), usernames), getAnalysisRights.rights(analysis, access)))
                .toList();
    }

    private AnalysisOwner owner(OwnerId owner, Map<UserId, Username> usernames) {
        if (owner == null) {
            return null;
        }
        return new AnalysisOwner(owner, usernames.get(new UserId(owner.value())));
    }
}
