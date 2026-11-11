package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ListAnalysesPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisScope;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;

@Service
class AnalysisListService implements ListAnalysesPort {

    private final AnalysisRepositoryPort analysisRepositoryPort;

    AnalysisListService(AnalysisRepositoryPort analysisRepositoryPort) {
        this.analysisRepositoryPort = analysisRepositoryPort;
    }

    @Override
    public List<Analysis> list(AnalysisFilter filter, AnalysisScope scope, AnalysisAccess access) {
        if (scope == AnalysisScope.ALL && access.allUsers()) {
            return analysisRepositoryPort.findAll(filter);
        }
        if (scope == AnalysisScope.SHARED) {
            return analysisRepositoryPort.findAll(publicOfOthersThan(filter, access.user()));
        }
        return analysisRepositoryPort.findAll(ownedBy(filter, access.user()));
    }

    private AnalysisFilter ownedBy(AnalysisFilter filter, OwnerId user) {
        return new AnalysisFilter(filter.status(), filter.ticker(), user, Set.of(), null);
    }

    private AnalysisFilter publicOfOthersThan(AnalysisFilter filter, OwnerId user) {
        return new AnalysisFilter(
                filter.status(), filter.ticker(), null, Set.of(Visibility.PUBLIC, Visibility.PARTIAL), user);
    }
}
