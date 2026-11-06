package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetPartialAnalysisPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ListPartialAnalysesPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.PartialAnalysisPage;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;

@Service
class PartialAnalysisService implements GetPartialAnalysisPort, ListPartialAnalysesPort {

    private final AnalysisRepositoryPort repository;

    private final AnalysisIdService analysisIdService;

    PartialAnalysisService(AnalysisRepositoryPort repository, AnalysisIdService analysisIdService) {
        this.repository = repository;
        this.analysisIdService = analysisIdService;
    }

    @Override
    public Analysis get(AnalysisId id) {
        analysisIdService.requireValid(id);
        return repository
                .findById(id)
                .filter(analysis ->
                        analysis.visibility() == Visibility.PARTIAL && analysis.status() == AnalysisStatus.COMPLETED)
                .orElseThrow(() -> new AnalysisException(
                        AnalysisError.NOT_FOUND, "Analysis not found: " + id.value(), Map.of("id", id.value())));
    }

    @Override
    public PartialAnalysisPage list(int page, int size) {
        AnalysisFilter partial =
                new AnalysisFilter(AnalysisStatus.COMPLETED, null, null, Set.of(Visibility.PARTIAL), null);
        return repository.findPage(partial, page, size);
    }
}
