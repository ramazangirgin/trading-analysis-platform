package tr.girgin.backend.trading.analysis.platform.orchestration.analysisdefaults.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;

/** Reads and saves the platform's analysis defaults, checked against what the catalog offers. */
public interface ManageAnalysisDefaultsUseCase {

    /**
     * The stored defaults; while nothing is stored, the catalog's defaults with one round each and a {@code null}
     * version. Only that fallback asks the catalog, so only then can {@code CatalogUnavailableException} be thrown.
     */
    AnalysisDefaults get();

    /**
     * Validates and saves the defaults and returns them as stored, with their new version. A {@code null}
     * {@code expectedVersion} means nothing was stored when the client read.
     *
     * @throws tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException
     *     {@code INVALID_ANALYSIS_DEFAULTS} (param {@code field}) when the provider or a model is not in the catalog or
     *     a round count is outside 1..10; {@code CONCURRENT_UPDATE} when the stored version differs
     */
    AnalysisDefaults update(AnalysisDefaults defaults, Long expectedVersion);
}
