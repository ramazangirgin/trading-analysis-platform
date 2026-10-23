package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

/** Makes an analysis public or private. */
public interface ChangeAnalysisVisibilityUseCase {

    /**
     * Asking for the visibility the analysis has returns it unchanged.
     *
     * @throws tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException with
     *     NOT_FOUND when the caller may not see the analysis, may not make it public (only its owner may) or
     *     private; CONCURRENT_UPDATE when {@code version} is not the stored one; SHARE_FORBIDDEN when the owner
     *     lacks {@code analysis:share}; NOT_SHAREABLE when a public one is asked for and the run is not completed
     */
    Analysis change(AnalysisId id, Visibility visibility, AnalysisAccess access, Long version);
}
