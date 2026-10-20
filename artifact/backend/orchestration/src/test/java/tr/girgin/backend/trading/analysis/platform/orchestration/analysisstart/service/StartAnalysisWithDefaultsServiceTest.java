package tr.girgin.backend.trading.analysis.platform.orchestration.analysisstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelOption;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Provider;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisstart.model.RequestedAnalysis;

class StartAnalysisWithDefaultsServiceTest {

    private static final OwnerId OWNER = new OwnerId("u1");
    private static final AnalysisDefaults PLATFORM = new AnalysisDefaults("openai", "gpt-deep", "gpt-quick", 3, 2, 1L);
    private static final Catalog CATALOG = new Catalog(
            "1.0",
            new ModelDefaults("openai", "gpt-deep", "gpt-quick"),
            List.of(
                    new Provider(
                            "openai",
                            "OPENAI_API_KEY",
                            false,
                            List.of(new ModelOption("gpt-quick", "Quick"), new ModelOption("gpt-fast", "Fast")),
                            List.of(new ModelOption("gpt-deep", "Deep"), new ModelOption("gpt-big", "Big"))),
                    new Provider(
                            "ollama",
                            null,
                            true,
                            List.of(new ModelOption("llama", "Llama")),
                            List.of(new ModelOption("llama", "Llama")))),
            List.of(),
            List.of());

    private final List<AnalysisSpec> started = new ArrayList<>();
    private int catalogCalls;
    private final StartAnalysisWithDefaultsService service = new StartAnalysisWithDefaultsService(
            new FakeDefaults(),
            () -> {
                catalogCalls++;
                return CATALOG;
            },
            (spec, owner) -> {
                started.add(spec);
                return Analysis.queued(new AnalysisId("r_1"), spec, owner, Instant.EPOCH);
            });

    @Test
    void usesThePlatformValuesWhenNothingIsSent() {
        service.start(requested(null, null, null, null, null), OWNER, false);

        assertThat(started).singleElement().satisfies(spec -> {
            assertThat(spec.llmProvider()).isEqualTo("openai");
            assertThat(spec.deepThinkLlm()).isEqualTo("gpt-deep");
            assertThat(spec.quickThinkLlm()).isEqualTo("gpt-quick");
            assertThat(spec.maxDebateRounds()).isEqualTo(3);
            assertThat(spec.maxRiskDiscussRounds()).isEqualTo(2);
            assertThat(spec.assetType()).isEqualTo(AssetType.STOCK);
            assertThat(spec.outputLanguage()).isEqualTo("English");
            assertThat(spec.checkpointEnabled()).isFalse();
        });
        assertThat(catalogCalls).isZero();
    }

    @Test
    void acceptsExactlyThePlatformValuesWithoutThePermission() {
        service.start(requested("openai", "gpt-deep", "gpt-quick", 3, 2), OWNER, false);

        assertThat(started).hasSize(1);
        assertThat(catalogCalls).isZero();
    }

    @Test
    void refusesADifferingValueWithoutThePermissionAndStartsNothing() {
        assertThatThrownBy(() -> service.start(requested(null, null, null, 4, null), OWNER, false))
                .isInstanceOfSatisfying(AnalysisException.class, e -> {
                    assertThat(e.error()).isEqualTo(AnalysisError.OVERRIDE_FORBIDDEN);
                    assertThat(e.params()).isEqualTo(Map.of("field", "maxDebateRounds"));
                });

        assertThat(started).isEmpty();
    }

    @Test
    void namesTheFirstDifferingField() {
        assertThatThrownBy(() -> service.start(requested("ollama", "x", null, null, 5), OWNER, false))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.params()).containsEntry("field", "llmProvider"));
        assertThatThrownBy(() -> service.start(requested(null, null, "gpt-fast", null, 5), OWNER, false))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.params()).containsEntry("field", "quickThinkLlm"));
    }

    @Test
    void mergesOverridesWithThePlatformValuesForTheRest() {
        service.start(requested(null, "gpt-big", null, 5, null), OWNER, true);

        assertThat(started).singleElement().satisfies(spec -> {
            assertThat(spec.llmProvider()).isEqualTo("openai");
            assertThat(spec.deepThinkLlm()).isEqualTo("gpt-big");
            assertThat(spec.quickThinkLlm()).isEqualTo("gpt-quick");
            assertThat(spec.maxDebateRounds()).isEqualTo(5);
            assertThat(spec.maxRiskDiscussRounds()).isEqualTo(2);
        });
    }

    @Test
    void refusesAnOverrideTheCatalogDoesNotOffer() {
        assertInvalid(requested("nope", "a", "b", null, null), "llmProvider");
        assertInvalid(requested(null, "llama", null, null, null), "deepThinkLlm");
        assertInvalid(requested(null, null, "llama", null, null), "quickThinkLlm");

        assertThat(started).isEmpty();
    }

    @Test
    void acceptsAnyModelIdForAProviderWithCustomModels() {
        service.start(requested("ollama", "my-deep", "my-quick", null, null), OWNER, true);

        assertThat(started).singleElement().satisfies(spec -> {
            assertThat(spec.llmProvider()).isEqualTo("ollama");
            assertThat(spec.deepThinkLlm()).isEqualTo("my-deep");
        });
    }

    @Test
    void doesNotAskTheCatalogForRoundsOnly() {
        service.start(requested(null, null, null, 4, null), OWNER, true);

        assertThat(catalogCalls).isZero();
    }

    private void assertInvalid(RequestedAnalysis requested, String field) {
        assertThatThrownBy(() -> service.start(requested, OWNER, true))
                .isInstanceOfSatisfying(AnalysisException.class, e -> {
                    assertThat(e.error()).isEqualTo(AnalysisError.INVALID_SPEC);
                    assertThat(e.params()).isEqualTo(Map.of("field", field));
                });
    }

    private static RequestedAnalysis requested(
            String provider, String deep, String quick, Integer debateRounds, Integer riskRounds) {
        return new RequestedAnalysis(
                "AAPL",
                LocalDate.of(2026, 1, 2),
                null,
                List.of(Analyst.MARKET),
                provider,
                deep,
                quick,
                debateRounds,
                riskRounds,
                null,
                null);
    }

    private static final class FakeDefaults
            implements tr.girgin.backend.trading.analysis.platform.orchestration.analysisdefaults.inbound
                    .ManageAnalysisDefaultsUseCase {

        @Override
        public AnalysisDefaults get() {
            return PLATFORM;
        }

        @Override
        public AnalysisDefaults update(AnalysisDefaults defaults, Long expectedVersion) {
            throw new UnsupportedOperationException();
        }
    }
}
