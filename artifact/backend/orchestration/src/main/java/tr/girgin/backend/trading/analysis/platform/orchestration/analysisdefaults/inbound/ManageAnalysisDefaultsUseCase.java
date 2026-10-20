package tr.girgin.backend.trading.analysis.platform.orchestration.analysisdefaults.inbound;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.inbound.GetCatalogUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelOption;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Provider;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsError;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence.AnalysisDefaultsRepositoryPort;

/** Reads and saves the platform's analysis defaults, checked against what the catalog offers. */
@Service
public class ManageAnalysisDefaultsUseCase {

    private static final int MIN_ROUNDS = 1;
    private static final int MAX_ROUNDS = 10;

    private final AnalysisDefaultsRepositoryPort repository;
    private final GetCatalogUseCase catalog;

    public ManageAnalysisDefaultsUseCase(AnalysisDefaultsRepositoryPort repository, GetCatalogUseCase catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    /**
     * The stored defaults; while nothing is stored, the catalog's defaults with one round each and a {@code null}
     * version. Only that fallback asks the catalog, so only then can {@code CatalogUnavailableException} be thrown
     * by this method.
     */
    public AnalysisDefaults get() {
        return repository.find().orElseGet(this::fromCatalog);
    }

    /**
     * Validates and saves the defaults and returns them as stored, with their new version. A {@code null}
     * {@code expectedVersion} means nothing was stored when the client read.
     *
     * @throws SettingsException {@code INVALID_ANALYSIS_DEFAULTS} (param {@code field}) when the provider or a model
     *     is not in the catalog or a round count is outside 1..10; {@code CONCURRENT_UPDATE} when the stored version
     *     differs
     * @throws tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException
     *     when the catalog the values are checked against cannot be read
     */
    public AnalysisDefaults update(AnalysisDefaults defaults, Long expectedVersion) {
        validate(defaults, catalog.getCatalog());
        try {
            return repository.save(new AnalysisDefaults(
                    defaults.llmProvider(),
                    defaults.deepThinkLlm(),
                    defaults.quickThinkLlm(),
                    defaults.maxDebateRounds(),
                    defaults.maxRiskDiscussRounds(),
                    expectedVersion));
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException _) {
            throw new SettingsException(
                    SettingsError.CONCURRENT_UPDATE,
                    "Analysis defaults were changed concurrently",
                    expectedVersion == null ? Map.of() : Map.of("version", expectedVersion.toString()));
        }
    }

    private AnalysisDefaults fromCatalog() {
        ModelDefaults defaults = catalog.getCatalog().defaults();
        return new AnalysisDefaults(
                defaults.llmProvider(), defaults.deepThinkLlm(), defaults.quickThinkLlm(), 1, 1, null);
    }

    private static void validate(AnalysisDefaults defaults, Catalog catalog) {
        Provider provider = catalog.providers().stream()
                .filter(p -> p.id().equals(defaults.llmProvider()))
                .findFirst()
                .orElseThrow(() -> invalid("llmProvider", "Unknown provider: " + defaults.llmProvider()));
        requireListed(provider.deepModels(), defaults.deepThinkLlm(), "deepThinkLlm");
        requireListed(provider.quickModels(), defaults.quickThinkLlm(), "quickThinkLlm");
        requireRounds(defaults.maxDebateRounds(), "maxDebateRounds");
        requireRounds(defaults.maxRiskDiscussRounds(), "maxRiskDiscussRounds");
    }

    private static void requireListed(List<ModelOption> models, String model, String field) {
        if (models.stream().noneMatch(m -> m.id().equals(model))) {
            throw invalid(field, "Model is not offered by the provider: " + model);
        }
    }

    private static void requireRounds(int rounds, String field) {
        if (rounds < MIN_ROUNDS || rounds > MAX_ROUNDS) {
            throw invalid(field, "Rounds must be between " + MIN_ROUNDS + " and " + MAX_ROUNDS);
        }
    }

    private static SettingsException invalid(String field, String message) {
        return new SettingsException(SettingsError.INVALID_ANALYSIS_DEFAULTS, message, Map.of("field", field));
    }
}
