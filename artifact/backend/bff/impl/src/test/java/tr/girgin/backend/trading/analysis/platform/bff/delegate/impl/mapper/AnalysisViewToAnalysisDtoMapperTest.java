package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisSourceEnumDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisStatusEnumDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalystEnumDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AssetTypeEnumDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RatingEnumDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.VisibilityEnumDto;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.AnalysisRights;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.library.mapper.DurationToMillisMapperImpl;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.model.AnalysisOwner;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.model.AnalysisView;

class AnalysisViewToAnalysisDtoMapperTest {

    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05Z");
    private static final Instant STARTED = Instant.parse("2026-01-02T03:05:00Z");
    private static final Instant ENDED = Instant.parse("2026-01-02T03:15:00Z");
    private static final Instant VISIBILITY_CHANGED = Instant.parse("2026-01-03T00:00:00Z");

    private final AnalysisViewToAnalysisDtoMapper analysisViewToAnalysisDtoMapper =
            new AnalysisViewToAnalysisDtoMapperImpl(
                    new AnalysisIdToStringMapperImpl(),
                    new RunStatsToRunStatsDtoMapperImpl(new DurationToMillisMapperImpl()),
                    new VisibilityToVisibilityEnumDtoMapperImpl());

    @Test
    void mapsEveryAnalysisFieldAndTheRights() {
        AnalysisView view = new AnalysisView(
                analysis(new OwnerId("u-1")),
                new AnalysisOwner(new OwnerId("u-1"), new Username("alice")),
                new AnalysisRights(true, true, List.of(Visibility.PRIVATE, Visibility.PUBLIC, Visibility.PARTIAL)));

        AnalysisDto dto = analysisViewToAnalysisDtoMapper.map(view);

        assertThat(dto.id()).isEqualTo("a-1");
        assertThat(dto.spec().ticker()).isEqualTo("NVDA");
        assertThat(dto.spec().tradeDate()).isEqualTo(LocalDate.parse("2026-01-01"));
        assertThat(dto.spec().assetType()).isEqualTo(AssetTypeEnumDto.STOCK);
        assertThat(dto.spec().analysts()).containsExactly(AnalystEnumDto.MARKET, AnalystEnumDto.NEWS);
        assertThat(dto.spec().llmProvider()).isEqualTo("openai");
        assertThat(dto.spec().deepThinkLlm()).isEqualTo("deep");
        assertThat(dto.spec().quickThinkLlm()).isEqualTo("quick");
        assertThat(dto.spec().maxDebateRounds()).isEqualTo(2);
        assertThat(dto.spec().maxRiskDiscussRounds()).isEqualTo(3);
        assertThat(dto.spec().outputLanguage()).isEqualTo("English");
        assertThat(dto.spec().checkpointEnabled()).isTrue();
        assertThat(dto.status()).isEqualTo(AnalysisStatusEnumDto.COMPLETED);
        assertThat(dto.source()).isEqualTo(AnalysisSourceEnumDto.PLATFORM);
        assertThat(dto.rating()).isEqualTo(RatingEnumDto.BUY);
        assertThat(dto.decision()).isEqualTo("Buy now");
        assertThat(dto.stats().llmCalls()).isEqualTo(4);
        assertThat(dto.stats().toolCalls()).isEqualTo(5);
        assertThat(dto.stats().tokensIn()).isEqualTo(600);
        assertThat(dto.stats().tokensOut()).isEqualTo(700);
        assertThat(dto.stats().costUsd()).isEqualByComparingTo("1.25");
        assertThat(dto.stats().elapsedMs()).isEqualTo(90_000);
        assertThat(dto.createdAt()).isEqualTo(CREATED);
        assertThat(dto.startedAt()).isEqualTo(STARTED);
        assertThat(dto.endedAt()).isEqualTo(ENDED);
        assertThat(dto.errorCode()).isEqualTo("code");
        assertThat(dto.errorMessage()).isEqualTo("message");
        assertThat(dto.visibility()).isEqualTo(VisibilityEnumDto.PUBLIC);
        assertThat(dto.visibilityChangedAt()).isEqualTo(VISIBILITY_CHANGED);
        assertThat(dto.version()).isEqualTo(7L);
        assertThat(dto.canManage()).isTrue();
        assertThat(dto.canChangeVisibility()).isTrue();
        assertThat(dto.allowedVisibilities())
                .containsExactly(VisibilityEnumDto.PRIVATE, VisibilityEnumDto.PUBLIC, VisibilityEnumDto.PARTIAL);
    }

    @Test
    void mapsTheOwnerWithItsUsername() {
        AnalysisView view = new AnalysisView(
                analysis(new OwnerId("u-1")),
                new AnalysisOwner(new OwnerId("u-1"), new Username("alice")),
                new AnalysisRights(false, false, List.of()));

        AnalysisDto dto = analysisViewToAnalysisDtoMapper.map(view);

        assertThat(dto.owner().id()).isEqualTo("u-1");
        assertThat(dto.owner().username()).isEqualTo("alice");
        assertThat(dto.canManage()).isFalse();
        assertThat(dto.canChangeVisibility()).isFalse();
        assertThat(dto.allowedVisibilities()).isEmpty();
    }

    @Test
    void mapsAnOwnerWhoIsNoLongerAUserWithoutUsername() {
        AnalysisView view = new AnalysisView(
                analysis(new OwnerId("u-1")),
                new AnalysisOwner(new OwnerId("u-1"), null),
                new AnalysisRights(false, false, List.of()));

        AnalysisDto dto = analysisViewToAnalysisDtoMapper.map(view);

        assertThat(dto.owner().id()).isEqualTo("u-1");
        assertThat(dto.owner().username()).isNull();
    }

    @Test
    void mapsNoOwnerToNull() {
        AnalysisView view = new AnalysisView(analysis(null), null, new AnalysisRights(false, false, List.of()));

        assertThat(analysisViewToAnalysisDtoMapper.map(view).owner()).isNull();
    }

    private static Analysis analysis(OwnerId owner) {
        return new Analysis(
                new AnalysisId("a-1"),
                new AnalysisSpec(
                        "NVDA",
                        LocalDate.parse("2026-01-01"),
                        AssetType.STOCK,
                        List.of(Analyst.MARKET, Analyst.NEWS),
                        "openai",
                        "deep",
                        "quick",
                        2,
                        3,
                        "English",
                        true),
                AnalysisStatus.COMPLETED,
                AnalysisSource.PLATFORM,
                Rating.BUY,
                "Buy now",
                new RunStats(4, 5, 600, 700, new BigDecimal("1.25"), Duration.ofSeconds(90)),
                CREATED,
                STARTED,
                ENDED,
                "code",
                "message",
                null,
                null,
                owner,
                Visibility.PUBLIC,
                VISIBILITY_CHANGED,
                7L);
    }
}
