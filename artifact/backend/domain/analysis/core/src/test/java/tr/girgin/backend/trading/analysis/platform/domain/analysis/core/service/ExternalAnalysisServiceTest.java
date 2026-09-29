package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration.Outcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;

class ExternalAnalysisServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 28);
    private static final Instant FINISHED = Instant.parse("2026-09-28T12:00:00Z");

    private final Fakes.Repository repository = new Fakes.Repository();
    private final ExternalAnalysisService service = new ExternalAnalysisService(repository);

    @Test
    void importsACompletedRun() {
        var registration = service.register(external("GOOG", Rating.HOLD, "**Rating**: Hold", FINISHED));

        Analysis analysis = registration.analysis();
        assertThat(registration.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(analysis.source()).isEqualTo(AnalysisSource.EXTERNAL);
        assertThat(analysis.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(analysis.rating()).isEqualTo(Rating.HOLD);
        assertThat(analysis.spec().llmProvider()).isEqualTo(Analysis.UNKNOWN);
        assertThat(analysis.spec().analysts()).containsExactly(Analyst.MARKET, Analyst.NEWS);
        assertThat(analysis.endedAt()).isEqualTo(FINISHED);
    }

    @Test
    void importsARunWithoutDecisionAsIncomplete() {
        Analysis analysis = service.register(external("NVDA", null, null, FINISHED)).analysis();

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.errorCode()).isEqualTo(Analysis.INCOMPLETE_REPORT);
    }

    @Test
    void isIdempotentAndFollowsChangedFiles() {
        service.register(external("GOOG", null, null, FINISHED));

        assertThat(service.register(external("GOOG", null, null, FINISHED)).outcome()).isEqualTo(Outcome.UNCHANGED);
        var updated = service.register(external("GOOG", Rating.SELL, "Sell", FINISHED.plusSeconds(60)));

        assertThat(updated.outcome()).isEqualTo(Outcome.UPDATED);
        assertThat(repository.findAll(AnalysisFilter.ALL)).singleElement()
                .satisfies(a -> assertThat(a.status()).isEqualTo(AnalysisStatus.COMPLETED));
    }

    @Test
    void leavesTickersAndDatesThePlatformRanAlone() {
        Analysis platformRun = Analysis.queued(AnalysisId.newId(), Fakes.spec("MU", DATE), Instant.now());
        repository.insert(platformRun);

        var registration = service.register(external("MU", Rating.BUY, "Buy", FINISHED));

        assertThat(registration.outcome()).isEqualTo(Outcome.UNCHANGED);
        assertThat(registration.analysis().id()).isEqualTo(platformRun.id());
        assertThat(repository.findAll(AnalysisFilter.ALL)).hasSize(1);
    }

    private static ExternalAnalysis external(String ticker, Rating rating, String decision, Instant finishedAt) {
        return new ExternalAnalysis(ticker, DATE, List.of(Analyst.NEWS, Analyst.MARKET), rating, decision, finishedAt);
    }
}
