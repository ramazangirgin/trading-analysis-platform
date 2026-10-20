package tr.girgin.backend.trading.analysis.platform.orchestration.analysisdefaults.service;

import java.util.List;
import java.util.Map;
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
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisdefaults.inbound.ManageAnalysisDefaultsUseCase;

@Service
class AnalysisDefaultsService implements ManageAnalysisDefaultsUseCase {

    private static final int MIN_ROUNDS = 1;
    private static final int MAX_ROUNDS = 10;

    private final AnalysisDefaultsRepositoryPort repository;
    private final GetCatalogUseCase catalog;

    AnalysisDefaultsService(AnalysisDefaultsRepositoryPort repository, GetCatalogUseCase catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Override
    public AnalysisDefaults get() {
        return repository.find().orElseGet(this::fromCatalog);
    }

    @Override
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
        } catch (OptimisticLockingFailureException _) {
            throw new SettingsException(
                    SettingsError.CONCURRENT_UPDATE,
                    "Analysis defaults were changed concurrently",
                    Map.of("version", String.valueOf(expectedVersion)));
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
