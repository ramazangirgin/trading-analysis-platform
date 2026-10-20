package tr.girgin.backend.trading.analysis.platform.orchestration.analysisstart.inbound;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.StartAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.inbound.GetCatalogUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelOption;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Provider;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisdefaults.inbound.ManageAnalysisDefaultsUseCase;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisstart.model.RequestedAnalysis;

/** Starts an analysis whose provider, models and rounds the server decides. */
@Service
public class StartAnalysisWithDefaultsUseCase {

    private final ManageAnalysisDefaultsUseCase defaults;
    private final GetCatalogUseCase catalog;
    private final StartAnalysisUseCase startAnalysis;

    StartAnalysisWithDefaultsUseCase(
            ManageAnalysisDefaultsUseCase defaults, GetCatalogUseCase catalog, StartAnalysisUseCase startAnalysis) {
        this.defaults = defaults;
        this.catalog = catalog;
        this.startAnalysis = startAnalysis;
    }

    /**
     * Fills the provider, models and rounds that were not sent from the platform's analysis defaults and starts the
     * run.
     *
     * @throws AnalysisException {@code OVERRIDE_FORBIDDEN} (param {@code field}) when a sent value differs from the
     *     platform's and {@code mayOverrideModels} is false; {@code INVALID_SPEC} (param {@code field}) when an
     *     override is not in the catalog, or the analysis domain refuses the spec
     * @throws tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException
     *     when the catalog has to be read and cannot be
     */
    public Analysis start(RequestedAnalysis requested, OwnerId owner, boolean mayOverrideModels) {
        AnalysisDefaults platform = defaults.get();
        Optional<String> provider = override(requested.llmProvider(), platform.llmProvider());
        Optional<String> deep = override(requested.deepThinkLlm(), platform.deepThinkLlm());
        Optional<String> quick = override(requested.quickThinkLlm(), platform.quickThinkLlm());
        Optional<Integer> debateRounds = override(requested.maxDebateRounds(), platform.maxDebateRounds());
        Optional<Integer> riskRounds = override(requested.maxRiskDiscussRounds(), platform.maxRiskDiscussRounds());

        boolean overridden = provider.isPresent()
                || deep.isPresent()
                || quick.isPresent()
                || debateRounds.isPresent()
                || riskRounds.isPresent();
        if (overridden) {
            if (!mayOverrideModels) {
                throw forbidden(provider, deep, quick, debateRounds, riskRounds);
            }
            if (provider.isPresent() || deep.isPresent() || quick.isPresent()) {
                checkAgainstCatalog(
                        provider.orElse(platform.llmProvider()),
                        deep.orElse(platform.deepThinkLlm()),
                        quick.orElse(platform.quickThinkLlm()));
            }
        }

        return startAnalysis.start(
                new AnalysisSpec(
                        requested.ticker(),
                        requested.tradeDate(),
                        requested.assetType() == null ? AssetType.STOCK : requested.assetType(),
                        requested.analysts(),
                        provider.orElse(platform.llmProvider()),
                        deep.orElse(platform.deepThinkLlm()),
                        quick.orElse(platform.quickThinkLlm()),
                        debateRounds.orElse(platform.maxDebateRounds()),
                        riskRounds.orElse(platform.maxRiskDiscussRounds()),
                        requested.outputLanguage() == null ? "English" : requested.outputLanguage(),
                        Boolean.TRUE.equals(requested.checkpointEnabled())),
                owner);
    }

    private static <T> Optional<T> override(T sent, T platform) {
        if (sent == null || sameValue(sent, platform)) {
            return Optional.empty();
        }
        return Optional.of(sent);
    }

    private static boolean sameValue(Object sent, Object platform) {
        if (sent instanceof String s && platform instanceof String p) {
            return s.strip().equalsIgnoreCase(p);
        }
        return sent.equals(platform);
    }

    private static AnalysisException forbidden(
            Optional<String> provider,
            Optional<String> deep,
            Optional<String> quick,
            Optional<Integer> debateRounds,
            Optional<Integer> riskRounds) {
        String field = provider.isPresent()
                ? "llmProvider"
                : deep.isPresent()
                        ? "deepThinkLlm"
                        : quick.isPresent()
                                ? "quickThinkLlm"
                                : debateRounds.isPresent() ? "maxDebateRounds" : "maxRiskDiscussRounds";
        return new AnalysisException(
                AnalysisError.OVERRIDE_FORBIDDEN,
                "Changing the provider, models or rounds requires a permission: " + field,
                Map.of("field", field));
    }

    private void checkAgainstCatalog(String providerId, String deep, String quick) {
        Provider provider = catalog.getCatalog().providers().stream()
                .filter(p -> p.id().equalsIgnoreCase(providerId.strip()))
                .findFirst()
                .orElseThrow(() -> invalid("llmProvider", "Unknown provider: " + providerId));
        if (provider.customModelAllowed()) {
            return;
        }
        requireListed(provider.deepModels(), deep, "deepThinkLlm");
        requireListed(provider.quickModels(), quick, "quickThinkLlm");
    }

    private static void requireListed(List<ModelOption> models, String model, String field) {
        if (models.stream().noneMatch(m -> m.id().equals(model))) {
            throw invalid(field, "Model is not offered by the provider: " + model);
        }
    }

    private static AnalysisException invalid(String field, String message) {
        return new AnalysisException(AnalysisError.INVALID_SPEC, message, Map.of("field", field));
    }
}
