package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RegisterExternalAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration.Outcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;

/**
 * Keeps one EXTERNAL record per data dir source (see {@code ref}) in step with the
 * files. A ticker and date the platform ran itself is left alone: its report files are that run's
 * output, not a new run.
 */
@Service
class ExternalAnalysisService implements RegisterExternalAnalysisUseCase {

    private final AnalysisRepositoryPort repository;

    private final AnalysisTransitionService transitions;
    private final AnalysisIdService ids;

    ExternalAnalysisService(
            AnalysisRepositoryPort repository, AnalysisTransitionService transitions, AnalysisIdService ids) {
        this.repository = repository;
        this.transitions = transitions;
        this.ids = ids;
    }

    @Override
    public synchronized ExternalRegistration register(ExternalAnalysis external, OwnerId owner) {
        if (external.origin() == ExternalAnalysis.Origin.REPORT_FILES) {
            Optional<Analysis> platformRun = platformRun(external);
            if (platformRun.isPresent()) {
                return new ExternalRegistration(platformRun.get(), Outcome.UNCHANGED);
            }
        }
        String ref = ref(external);
        Optional<Analysis> existing = repository.findByExternalRef(ref);
        if (existing.isEmpty()) {
            Analysis created = transitions.imported(ids.newId(), null, owner, external, ref);
            repository.insert(created);
            return new ExternalRegistration(created, Outcome.CREATED);
        }
        Analysis current = existing.get();
        OwnerId refreshedOwner = current.owner() != null ? current.owner() : owner;
        Analysis refreshed = transitions.withVisibilityOf(
                transitions.imported(current.id(), current.version(), refreshedOwner, external, ref), current);
        if (refreshed.equals(current)) {
            return new ExternalRegistration(current, Outcome.UNCHANGED);
        }
        try {
            return new ExternalRegistration(repository.replaceImported(refreshed), Outcome.UPDATED);
        } catch (OptimisticLockingFailureException _) {
            throw new AnalysisException(
                    AnalysisError.CONCURRENT_UPDATE,
                    "Analysis was changed concurrently: " + current.id().value(),
                    Map.of("id", current.id().value()));
        }
    }

    // Stable identity in the data dir, so a rescan finds the same record.
    private String ref(ExternalAnalysis external) {
        return switch (external.origin()) {
            case REPORT_FILES -> "report:" + external.ticker() + "/" + external.tradeDate();
            case RUN_HISTORY -> "run:" + external.run().id();
        };
    }

    private Optional<Analysis> platformRun(ExternalAnalysis external) {
        return repository.findAll(new AnalysisFilter(null, external.ticker(), null, Set.of(), null)).stream()
                .filter(a -> a.source() == AnalysisSource.PLATFORM)
                .filter(a -> a.spec().tradeDate().equals(external.tradeDate()))
                .findFirst();
    }
}
