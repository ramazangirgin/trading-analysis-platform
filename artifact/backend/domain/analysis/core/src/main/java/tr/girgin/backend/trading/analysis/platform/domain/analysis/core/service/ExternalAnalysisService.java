package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.util.Objects;
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
 * Keeps one EXTERNAL record per ticker and trade date in step with the data dir. A ticker and date
 * the platform ran itself is left alone: its report files are that run's output, not a new run.
 */
@Service
class ExternalAnalysisService implements RegisterExternalAnalysisUseCase {

    private final AnalysisRepositoryPort repository;

    ExternalAnalysisService(AnalysisRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public synchronized ExternalRegistration register(ExternalAnalysis external) {
        Analysis candidate = Analysis.imported(AnalysisId.newId(), external);
        String ticker = candidate.spec().ticker();
        var sameRun = repository.findAll(new AnalysisFilter(null, ticker)).stream()
                .filter(a -> a.spec().tradeDate().equals(external.tradeDate()))
                .toList();
        Optional<Analysis> platformRun = sameRun.stream().filter(a -> a.source() == AnalysisSource.PLATFORM).findFirst();
        if (platformRun.isPresent()) {
            return new ExternalRegistration(platformRun.get(), Outcome.UNCHANGED);
        }
        Optional<Analysis> existing = sameRun.stream().findFirst();
        if (existing.isEmpty()) {
            repository.insert(candidate);
            return new ExternalRegistration(candidate, Outcome.CREATED);
        }
        Analysis current = existing.get();
        Analysis refreshed = Analysis.imported(current.id(), external);
        if (sameContent(current, refreshed)) {
            return new ExternalRegistration(current, Outcome.UNCHANGED);
        }
        repository.update(refreshed);
        return new ExternalRegistration(refreshed, Outcome.UPDATED);
    }

    private static boolean sameContent(Analysis a, Analysis b) {
        return a.status() == b.status()
                && a.rating() == b.rating()
                && Objects.equals(a.decision(), b.decision())
                && Objects.equals(a.endedAt(), b.endedAt());
    }
}
