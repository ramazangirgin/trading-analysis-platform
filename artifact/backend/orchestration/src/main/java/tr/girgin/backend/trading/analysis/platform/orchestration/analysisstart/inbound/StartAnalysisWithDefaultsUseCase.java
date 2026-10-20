package tr.girgin.backend.trading.analysis.platform.orchestration.analysisstart.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisstart.model.RequestedAnalysis;

/** Starts an analysis whose provider, models and rounds the server decides. */
public interface StartAnalysisWithDefaultsUseCase {

    /**
     * Fills the provider, models and rounds that were not sent from the platform's analysis defaults and starts the
     * run.
     *
     * @throws tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException
     *     {@code OVERRIDE_FORBIDDEN} (param {@code field}) when a sent value differs from the platform's and
     *     {@code mayOverrideModels} is false; {@code INVALID_SPEC} (param {@code field}) when an override is not in
     *     the catalog, or the analysis domain refuses the spec
     * @throws tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException
     *     when the catalog has to be read and cannot be
     */
    Analysis start(RequestedAnalysis requested, OwnerId owner, boolean mayOverrideModels);
}
