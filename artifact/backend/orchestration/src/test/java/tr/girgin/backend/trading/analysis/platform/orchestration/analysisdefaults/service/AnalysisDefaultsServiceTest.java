package tr.girgin.backend.trading.analysis.platform.orchestration.analysisdefaults.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelOption;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Provider;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsError;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence.AnalysisDefaultsRepositoryPort;

class AnalysisDefaultsServiceTest {

    private static final Catalog CATALOG = new Catalog(
            "1.0",
            new ModelDefaults("openai", "gpt-deep", "gpt-quick"),
            List.of(
                    new Provider(
                            "openai",
                            "OPENAI_API_KEY",
                            false,
                            List.of(new ModelOption("gpt-quick", "Quick")),
                            List.of(new ModelOption("gpt-deep", "Deep"))),
                    new Provider(
                            "anthropic",
                            "ANTHROPIC_API_KEY",
                            false,
                            List.of(new ModelOption("claude-quick", "Quick")),
                            List.of(new ModelOption("claude-deep", "Deep")))),
            List.of(),
            List.of());

    private final FakeRepository repository = new FakeRepository();
    private int catalogCalls;
    private final AnalysisDefaultsService service = new AnalysisDefaultsService(repository, () -> {
        catalogCalls++;
        return CATALOG;
    });

    @Test
    void answersTheCatalogDefaultsWithOneRoundEachWhileNothingIsStored() {
        assertThat(service.get()).isEqualTo(new AnalysisDefaults("openai", "gpt-deep", "gpt-quick", 1, 1, null));
    }

    @Test
    void answersTheStoredRowWithoutAskingTheCatalog() {
        repository.store(new AnalysisDefaults("anthropic", "claude-deep", "claude-quick", 3, 4, 2L));

        assertThat(service.get()).isEqualTo(repository.stored());
        assertThat(catalogCalls).isZero();
    }

    @Test
    void refusesAnUnknownProvider() {
        assertInvalid(new AnalysisDefaults("nope", "gpt-deep", "gpt-quick", 1, 1, null), "llmProvider");
    }

    @Test
    void refusesADeepModelThatTheProviderDoesNotList() {
        assertInvalid(new AnalysisDefaults("openai", "claude-deep", "gpt-quick", 1, 1, null), "deepThinkLlm");
    }

    @Test
    void refusesAQuickModelThatTheProviderDoesNotList() {
        assertInvalid(new AnalysisDefaults("openai", "gpt-deep", "claude-quick", 1, 1, null), "quickThinkLlm");
    }

    @Test
    void refusesRoundsOutsideOneToTen() {
        assertInvalid(new AnalysisDefaults("openai", "gpt-deep", "gpt-quick", 0, 1, null), "maxDebateRounds");
        assertInvalid(new AnalysisDefaults("openai", "gpt-deep", "gpt-quick", 1, 11, null), "maxRiskDiscussRounds");
    }

    @Test
    void storesTheFirstSaveAndAnswersItsVersion() {
        AnalysisDefaults saved =
                service.update(new AnalysisDefaults("openai", "gpt-deep", "gpt-quick", 2, 3, null), null);

        assertThat(saved.version()).isZero();
        assertThat(service.get()).isEqualTo(saved);
    }

    @Test
    void updatesAtTheStoredVersion() {
        repository.store(new AnalysisDefaults("openai", "gpt-deep", "gpt-quick", 1, 1, 0L));

        AnalysisDefaults saved =
                service.update(new AnalysisDefaults("anthropic", "claude-deep", "claude-quick", 5, 6, 0L), 0L);

        assertThat(saved).isEqualTo(new AnalysisDefaults("anthropic", "claude-deep", "claude-quick", 5, 6, 1L));
    }

    @Test
    void failsOnAStaleVersion() {
        repository.store(new AnalysisDefaults("openai", "gpt-deep", "gpt-quick", 1, 1, 3L));

        assertConcurrent(2L);
    }

    @Test
    void failsOnANullVersionWhileARowExists() {
        repository.store(new AnalysisDefaults("openai", "gpt-deep", "gpt-quick", 1, 1, 0L));

        assertConcurrent(null);
    }

    private void assertConcurrent(Long expectedVersion) {
        AnalysisDefaults before = repository.stored();

        assertThatThrownBy(() -> service.update(
                        new AnalysisDefaults("openai", "gpt-deep", "gpt-quick", 2, 2, expectedVersion),
                        expectedVersion))
                .isInstanceOfSatisfying(SettingsException.class, e -> {
                    assertThat(e.error()).isEqualTo(SettingsError.CONCURRENT_UPDATE);
                    if (expectedVersion == null) {
                        assertThat(e.params()).doesNotContainKey("version");
                    } else {
                        assertThat(e.params()).containsEntry("version", expectedVersion.toString());
                    }
                });
        assertThat(repository.stored()).isEqualTo(before);
    }

    private void assertInvalid(AnalysisDefaults defaults, String field) {
        assertThatThrownBy(() -> service.update(defaults, null)).isInstanceOfSatisfying(SettingsException.class, e -> {
            assertThat(e.error()).isEqualTo(SettingsError.INVALID_ANALYSIS_DEFAULTS);
            assertThat(e.params()).containsEntry("field", field);
        });
    }

    private static final class FakeRepository implements AnalysisDefaultsRepositoryPort {

        private AnalysisDefaults stored;

        void store(AnalysisDefaults defaults) {
            stored = defaults;
        }

        AnalysisDefaults stored() {
            return stored;
        }

        @Override
        public Optional<AnalysisDefaults> find() {
            return Optional.ofNullable(stored);
        }

        @Override
        public AnalysisDefaults save(AnalysisDefaults defaults) {
            if (!Objects.equals(defaults.version(), stored == null ? null : stored.version())) {
                throw new OptimisticLockingFailureException("stale version");
            }
            long next = stored == null ? 0L : stored.version() + 1;
            stored = new AnalysisDefaults(
                    defaults.llmProvider(),
                    defaults.deepThinkLlm(),
                    defaults.quickThinkLlm(),
                    defaults.maxDebateRounds(),
                    defaults.maxRiskDiscussRounds(),
                    next);
            return stored;
        }
    }
}
