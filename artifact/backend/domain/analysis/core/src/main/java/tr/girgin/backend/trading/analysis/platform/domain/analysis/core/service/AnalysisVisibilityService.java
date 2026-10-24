package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ChangeAnalysisVisibilityUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;

@Service
class AnalysisVisibilityService implements ChangeAnalysisVisibilityUseCase {

    private final AnalysisRepositoryPort repository;
    private final Clock clock;

    AnalysisVisibilityService(AnalysisRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Analysis change(AnalysisId id, Visibility visibility, AnalysisAccess access, Long version) {
        Analysis analysis = repository.findById(id).filter(access::canSee).orElseThrow(() -> notFound(id));
        if (!Objects.equals(version, analysis.version())) {
            throw concurrentUpdate(id);
        }
        if (analysis.visibility() == visibility) {
            return analysis;
        }
        if (!access.canChangeTo(analysis, visibility)) {
            throw refusal(analysis, visibility, access);
        }
        if (visibility != Visibility.PRIVATE && analysis.status() != AnalysisStatus.COMPLETED) {
            throw new AnalysisException(
                    AnalysisError.NOT_SHAREABLE,
                    "Only a completed analysis can be shared: " + id,
                    Map.of("id", id.value(), "status", analysis.status().name()));
        }
        try {
            return repository.update(analysis.withVisibility(visibility, clock.instant()));
        } catch (OptimisticLockingFailureException _) {
            throw concurrentUpdate(id);
        }
    }

    // Only the owner who lacks the permission learns why; anyone else must not learn the analysis can be shared.
    private AnalysisException refusal(Analysis analysis, Visibility target, AnalysisAccess access) {
        if (target != Visibility.PRIVATE && access.isOwner(analysis) && !access.share()) {
            return new AnalysisException(
                    AnalysisError.SHARE_FORBIDDEN,
                    "Missing the permission to share analyses",
                    Map.of("id", analysis.id().value()));
        }
        return notFound(analysis.id());
    }

    private AnalysisException notFound(AnalysisId id) {
        return new AnalysisException(AnalysisError.NOT_FOUND, "Analysis not found: " + id, Map.of("id", id.value()));
    }

    private AnalysisException concurrentUpdate(AnalysisId id) {
        return new AnalysisException(
                AnalysisError.CONCURRENT_UPDATE, "Analysis was changed concurrently: " + id, Map.of("id", id.value()));
    }
}
