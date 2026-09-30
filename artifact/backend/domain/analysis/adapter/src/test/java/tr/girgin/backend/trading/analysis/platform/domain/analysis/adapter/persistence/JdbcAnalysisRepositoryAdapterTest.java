package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.AdapterTestSupport;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRun;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;

class JdbcAnalysisRepositoryAdapterTest extends AdapterTestSupport {

    @Autowired
    private JdbcAnalysisRepositoryAdapter repository;

    @Test
    void roundTripsAnAnalysisThroughEveryTransition() {
        Instant created = Instant.parse("2026-09-29T10:00:00.123Z");
        Analysis queued = Analysis.queued(AnalysisId.newId(), spec("NVDA"), created);
        repository.insert(queued);

        Analysis done = queued.running(created.plusSeconds(1))
                .withStats(new RunStats(12, 10, 77137, 48090, new BigDecimal("0.42"), Duration.ofMillis(260_500)))
                .withDecision(Rating.OVERWEIGHT, "Rating: Overweight")
                .finished(AnalysisStatus.COMPLETED, created.plusSeconds(261), null, null);
        repository.update(done);

        assertThat(repository.findById(queued.id())).contains(done);
        assertThat(repository.findById(queued.id()).orElseThrow().spec()).isEqualTo(queued.spec());
    }

    @Test
    void listsNewestFirstAndFilters() {
        Instant base = Instant.parse("2026-09-29T11:00:00Z");
        Analysis older = Analysis.queued(AnalysisId.newId(), spec("ZZOLD"), base);
        Analysis newer = Analysis.queued(AnalysisId.newId(), spec("ZZNEW"), base.plusMillis(1)).running(base);
        repository.insert(older);
        repository.insert(newer);

        List<Analysis> all = repository.findAll(AnalysisFilter.ALL);
        assertThat(all.indexOf(newer)).isLessThan(all.indexOf(older));
        assertThat(repository.findAll(new AnalysisFilter(null, "ZZOLD"))).containsExactly(older);
        assertThat(repository.findAll(new AnalysisFilter(AnalysisStatus.RUNNING, "ZZNEW"))).containsExactly(newer);
        assertThat(repository.findByStatusIn(Set.of(AnalysisStatus.QUEUED))).contains(older).doesNotContain(newer);
    }

    @Test
    void findsAndReplacesImportedRecordsByTheirSource() {
        Instant ended = Instant.parse("2026-09-27T21:34:35.383Z");
        ExternalRun run = new ExternalRun("zz-a2cd", AnalysisStatus.COMPLETED, null, "deepseek", "deepseek-v4-pro",
                "deepseek-v4-flash", 5, "English",
                new RunStats(10, 0, 68_794, 28_916, BigDecimal.valueOf(0.0277), Duration.ofMillis(1_398_650)),
                ended.minusSeconds(1398), ended);
        Analysis imported = Analysis.imported(AnalysisId.newId(),
                ExternalAnalysis.reportFiles("ZZIMP", LocalDate.of(2026, 9, 27), List.of(), null, null, null, ended));
        repository.insert(imported);

        Analysis replaced = Analysis.imported(imported.id(), ExternalAnalysis.reportFiles("ZZIMP",
                LocalDate.of(2026, 9, 27), List.of(Analyst.MARKET), Rating.HOLD, "Hold", run, ended));
        repository.replaceImported(replaced);

        assertThat(repository.findByExternalRef("report:ZZIMP/2026-09-27")).contains(replaced);
        assertThat(repository.findByExternalRef("run:zz-a2cd")).isEmpty();
    }

    @Test
    void platformRunsAreNeverReplacedAsImported() {
        Analysis queued = Analysis.queued(AnalysisId.newId(), spec("NVDA"), Instant.now());
        repository.insert(queued);

        assertThatThrownBy(() -> repository.replaceImported(queued)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updatingAMissingAnalysisFails() {
        Analysis ghost = Analysis.queued(AnalysisId.newId(), spec("NVDA"), Instant.now());

        assertThatThrownBy(() -> repository.update(ghost)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownIdsAreEmpty() {
        assertThat(repository.findById(new AnalysisId("r_nope"))).isEmpty();
    }
}
