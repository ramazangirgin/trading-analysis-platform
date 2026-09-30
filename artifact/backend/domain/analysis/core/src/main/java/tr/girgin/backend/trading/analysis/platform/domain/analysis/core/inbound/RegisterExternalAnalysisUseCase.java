package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration;

public interface RegisterExternalAnalysisUseCase {

    /** Idempotent per data dir source; runs the platform started are never overwritten. */
    ExternalRegistration register(ExternalAnalysis external);
}
