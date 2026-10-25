package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetPartialAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ListPartialAnalysesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.PartialAnalysisPage;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;

@Service
class PartialAnalysisService implements GetPartialAnalysisUseCase, ListPartialAnalysesUseCase {

    private final AnalysisRepositoryPort repository;

    PartialAnalysisService(AnalysisRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Analysis get(AnalysisId id) {
        return repository
                .findById(id)
                .filter(analysis ->
                        analysis.visibility() == Visibility.PARTIAL && analysis.status() == AnalysisStatus.COMPLETED)
                .orElseThrow(() -> new AnalysisException(
                        AnalysisError.NOT_FOUND, "Analysis not found: " + id, Map.of("id", id.value())));
    }

    @Override
    public PartialAnalysisPage list(int page, int size) {
        return repository.findPage(AnalysisFilter.partial(), page, size);
    }
}
