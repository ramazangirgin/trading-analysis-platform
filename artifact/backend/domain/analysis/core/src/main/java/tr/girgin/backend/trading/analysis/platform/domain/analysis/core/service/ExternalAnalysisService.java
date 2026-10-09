package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RegisterExternalAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration.Outcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;

/**
 * Keeps one EXTERNAL record per data dir source ({@link ExternalAnalysis#ref()}) in step with the
 * files. A ticker and date the platform ran itself is left alone: its report files are that run's
 * output, not a new run.
 */
@Service
class ExternalAnalysisService implements RegisterExternalAnalysisUseCase {

    private final AnalysisRepositoryPort repository;

    ExternalAnalysisService(AnalysisRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public synchronized ExternalRegistration register(ExternalAnalysis external) {
        if (external.origin() == ExternalAnalysis.Origin.REPORT_FILES) {
            Optional<Analysis> platformRun = platformRun(external);
            if (platformRun.isPresent()) {
                return new ExternalRegistration(platformRun.get(), Outcome.UNCHANGED);
            }
        }
        Optional<Analysis> existing = repository.findByExternalRef(external.ref());
        if (existing.isEmpty()) {
            Analysis created = Analysis.imported(AnalysisId.newId(), null, external);
            repository.insert(created);
            return new ExternalRegistration(created, Outcome.CREATED);
        }
        Analysis current = existing.get();
        Analysis refreshed = Analysis.imported(current.id(), current.version(), external);
        if (refreshed.equals(current)) {
            return new ExternalRegistration(current, Outcome.UNCHANGED);
        }
        return new ExternalRegistration(repository.replaceImported(refreshed), Outcome.UPDATED);
    }

    private Optional<Analysis> platformRun(ExternalAnalysis external) {
        return repository.findAll(new AnalysisFilter(null, external.ticker())).stream()
                .filter(a -> a.source() == AnalysisSource.PLATFORM)
                .filter(a -> a.spec().tradeDate().equals(external.tradeDate()))
                .findFirst();
    }
}
