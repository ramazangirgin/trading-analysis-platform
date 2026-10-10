package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.AdapterTestSupport;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRun;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;

class JpaAnalysisRepositoryAdapterTest extends AdapterTestSupport {

    private static final int LIST_LIMIT = 500;

    @Autowired
    private JpaAnalysisRepositoryAdapter repository;

    @Test
    void roundTripsAnAnalysisThroughEveryTransition() {
        Instant created = Instant.parse("2026-09-29T10:00:00.123Z");
        Analysis queued = inserted(Analysis.queued(AnalysisId.newId(), spec("NVDA"), created));

        Analysis done = queued.running(created.plusSeconds(1), "4242@1790000000000")
                .withStats(new RunStats(12, 10, 77137, 48090, new BigDecimal("0.42"), Duration.ofMillis(260_500)))
                .withDecision(Rating.OVERWEIGHT, "Rating: Overweight")
                .finished(AnalysisStatus.COMPLETED, created.plusSeconds(261), null, null);
        Analysis updated = repository.update(done);

        assertThat(updated.version()).isEqualTo(1);
        assertThat(repository.findById(queued.id())).contains(updated);
        assertThat(updated).usingRecursiveComparison().ignoringFields("version").isEqualTo(done);
        assertThat(updated.spec()).isEqualTo(queued.spec());
    }

    @Test
    void aNewRecordStartsAtVersionZeroAndEveryWriteAddsOne() {
        Analysis queued = Analysis.queued(AnalysisId.newId(), spec("ZZVER"), Instant.now());
        assertThat(queued.version()).isNull();
        repository.insert(queued);
        Analysis stored = repository.findById(queued.id()).orElseThrow();
        assertThat(stored.version()).isZero();

        Analysis first = repository.update(stored.running(Instant.now(), "1"));
        Analysis second = repository.update(first.withDecision(Rating.HOLD, "Rating: Hold"));

        assertThat(first.version()).isEqualTo(1);
        assertThat(second.version()).isEqualTo(2);
        assertThat(repository.findById(queued.id()).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    void aStaleUpdateFailsAndLeavesTheNewerRowUnchanged() {
        // Millisecond instants: the column keeps microseconds, a Linux clock has nanoseconds.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Analysis stored = inserted(Analysis.queued(AnalysisId.newId(), spec("ZZSTALE"), now));
        Analysis firstCopy = repository.findById(stored.id()).orElseThrow();
        Analysis staleCopy = repository.findById(stored.id()).orElseThrow();
        Analysis firstWrite = repository.update(firstCopy.running(now, "first"));

        assertThatThrownBy(() -> repository.update(staleCopy.running(now, "second")))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(repository.findById(stored.id())).contains(firstWrite);
    }

    @Test
    void aStaleReplaceImportedFailsAndLeavesTheNewerRowUnchanged() {
        Instant ended = Instant.parse("2026-09-27T21:34:35.383Z");
        Analysis stored = inserted(Analysis.imported(
                AnalysisId.newId(),
                null,
                ExternalAnalysis.reportFiles(
                        "ZZSTALEIMP", LocalDate.of(2026, 9, 27), List.of(), null, null, null, ended)));
        Analysis staleCopy = repository.findById(stored.id()).orElseThrow();
        Analysis firstWrite = repository.replaceImported(Analysis.imported(
                stored.id(),
                stored.version(),
                ExternalAnalysis.reportFiles(
                        "ZZSTALEIMP",
                        LocalDate.of(2026, 9, 27),
                        List.of(Analyst.NEWS),
                        Rating.HOLD,
                        "Hold",
                        null,
                        ended)));

        assertThatThrownBy(() -> repository.replaceImported(Analysis.imported(
                        staleCopy.id(),
                        staleCopy.version(),
                        ExternalAnalysis.reportFiles(
                                "ZZSTALEIMP",
                                LocalDate.of(2026, 9, 27),
                                List.of(Analyst.MARKET),
                                Rating.SELL,
                                "Sell",
                                null,
                                ended))))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(repository.findById(stored.id())).contains(firstWrite);
    }

    @Test
    void aRecordWithoutAVersionCannotUpdateAStoredRow() {
        Analysis queued = Analysis.queued(AnalysisId.newId(), spec("ZZNULLVER"), Instant.now());
        repository.insert(queued);

        assertThatThrownBy(() -> repository.update(queued.running(Instant.now(), "x")))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void keepsTheTradeDateAndAllThreeTimestampsInTheirNativeTypes() {
        Instant created = Instant.parse("2026-09-29T23:59:59.123456Z");
        Analysis queued = inserted(Analysis.queued(AnalysisId.newId(), spec("ZZTYPES"), created));
        Analysis done = queued.running(created.plusNanos(1_000), "4244")
                .finished(AnalysisStatus.COMPLETED, created.plusSeconds(90).plusNanos(2_000), null, null);
        repository.update(done);

        Analysis found = repository.findById(queued.id()).orElseThrow();

        assertThat(found.createdAt()).isEqualTo(created);
        assertThat(found.startedAt()).isEqualTo(created.plusNanos(1_000));
        assertThat(found.endedAt()).isEqualTo(created.plusSeconds(90).plusNanos(2_000));
        assertThat(found.spec().tradeDate()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void listsNewestFirstAndFilters() {
        Instant base = Instant.parse("2026-09-29T11:00:00Z");
        Analysis older = inserted(Analysis.queued(AnalysisId.newId(), spec("ZZOLD"), base));
        Analysis newer = inserted(Analysis.queued(AnalysisId.newId(), spec("ZZNEW"), base.plusMillis(1)));
        newer = repository.update(newer.running(base, "4243"));

        List<Analysis> all = repository.findAll(AnalysisFilter.ALL);
        assertThat(all.indexOf(newer)).isLessThan(all.indexOf(older));
        assertThat(repository.findAll(new AnalysisFilter(null, "ZZOLD"))).containsExactly(older);
        assertThat(repository.findAll(new AnalysisFilter(AnalysisStatus.RUNNING, "ZZNEW")))
                .containsExactly(newer);
        assertThat(repository.findByStatusIn(Set.of(AnalysisStatus.QUEUED)))
                .contains(older)
                .doesNotContain(newer);
        assertThat(repository.findByStatusIn(Set.of())).isEmpty();
    }

    @Test
    void listIsLimitedToFiveHundredNewest() {
        Instant base = Instant.parse("2030-01-01T00:00:00Z");
        List<Analysis> inserted = new ArrayList<>();
        for (int i = 0; i <= LIST_LIMIT; i++) {
            inserted.add(inserted(Analysis.queued(AnalysisId.newId(), spec("ZZLIMIT"), base.plusSeconds(i))));
        }

        List<Analysis> listed = repository.findAll(AnalysisFilter.ALL);

        assertThat(listed).hasSize(LIST_LIMIT);
        assertThat(listed).doesNotContain(inserted.getFirst());
        assertThat(listed.getFirst()).isEqualTo(inserted.getLast());
    }

    @Test
    void findsAndReplacesImportedRecordsByTheirSource() {
        Instant ended = Instant.parse("2026-09-27T21:34:35.383Z");
        ExternalRun run = new ExternalRun(
                "zz-a2cd",
                AnalysisStatus.COMPLETED,
                null,
                "deepseek",
                "deepseek-v4-pro",
                "deepseek-v4-flash",
                5,
                "English",
                new RunStats(10, 0, 68_794, 28_916, BigDecimal.valueOf(0.0277), Duration.ofMillis(1_398_650)),
                ended.minusSeconds(1398),
                ended);
        Analysis imported = inserted(Analysis.imported(
                AnalysisId.newId(),
                null,
                ExternalAnalysis.reportFiles("ZZIMP", LocalDate.of(2026, 9, 27), List.of(), null, null, null, ended)));

        Analysis replaced = repository.replaceImported(Analysis.imported(
                imported.id(),
                imported.version(),
                ExternalAnalysis.reportFiles(
                        "ZZIMP", LocalDate.of(2026, 9, 27), List.of(Analyst.MARKET), Rating.HOLD, "Hold", run, ended)));

        assertThat(replaced.version()).isEqualTo(1);
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
    void updateKeepsTheSpecAndCreationTime() {
        Instant created = Instant.parse("2026-09-29T10:00:00Z");
        Analysis queued = inserted(Analysis.queued(AnalysisId.newId(), spec("ZZKEEP"), created));
        AnalysisSpec otherSpec = new AnalysisSpec(
                "ZZOTHER",
                LocalDate.of(2026, 1, 2),
                AssetType.CRYPTO,
                List.of(Analyst.SOCIAL),
                "openai",
                "a",
                "b",
                3,
                4,
                "English",
                false);
        Analysis changed = new Analysis(
                queued.id(),
                otherSpec,
                AnalysisStatus.RUNNING,
                queued.source(),
                null,
                null,
                queued.stats(),
                created.plusSeconds(3600),
                created.plusSeconds(1),
                null,
                null,
                null,
                null,
                "4245",
                queued.version());

        repository.update(changed);

        Analysis found = repository.findById(queued.id()).orElseThrow();
        assertThat(found.spec()).isEqualTo(queued.spec());
        assertThat(found.createdAt()).isEqualTo(created);
        assertThat(found.status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(found.runnerRef()).isEqualTo("4245");
    }

    @Test
    void insertingAnExistingIdFails() {
        Analysis queued = Analysis.queued(AnalysisId.newId(), spec("NVDA"), Instant.now());
        repository.insert(queued);

        assertThatThrownBy(() -> repository.insert(queued)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void unknownIdsAreEmpty() {
        assertThat(repository.findById(new AnalysisId("r_nope"))).isEmpty();
    }

    @Test
    void everyEnumConstantRoundTrips() {
        for (AnalysisStatus status : AnalysisStatus.values()) {
            Analysis analysis = analysis(status, AnalysisSource.PLATFORM, null, AssetType.STOCK, Analyst.MARKET);
            assertSameButForVersion(roundTrip(analysis), analysis);
        }
        for (AnalysisSource source : AnalysisSource.values()) {
            Analysis analysis = analysis(AnalysisStatus.QUEUED, source, null, AssetType.STOCK, Analyst.MARKET);
            assertThat(roundTrip(analysis).source()).isEqualTo(source);
        }
        for (AssetType assetType : AssetType.values()) {
            Analysis analysis =
                    analysis(AnalysisStatus.QUEUED, AnalysisSource.PLATFORM, null, assetType, Analyst.MARKET);
            assertThat(roundTrip(analysis).spec().assetType()).isEqualTo(assetType);
        }
        for (Rating rating : Rating.values()) {
            Analysis analysis = analysis(
                    AnalysisStatus.COMPLETED, AnalysisSource.PLATFORM, rating, AssetType.STOCK, Analyst.MARKET);
            assertThat(roundTrip(analysis).rating()).isEqualTo(rating);
        }
        for (Analyst analyst : Analyst.values()) {
            Analysis analysis =
                    analysis(AnalysisStatus.QUEUED, AnalysisSource.PLATFORM, null, AssetType.STOCK, analyst);
            assertThat(roundTrip(analysis).spec().analysts()).containsExactly(analyst);
        }
        Analysis everyAnalyst = Analysis.queued(
                AnalysisId.newId(),
                new AnalysisSpec(
                        "ZZALL",
                        LocalDate.of(2026, 9, 25),
                        AssetType.STOCK,
                        Arrays.asList(Analyst.values()),
                        "deepseek",
                        "a",
                        "b",
                        1,
                        1,
                        "English",
                        false),
                Instant.parse("2026-09-29T10:00:00Z"));
        assertSameButForVersion(roundTrip(everyAnalyst), everyAnalyst);
    }

    @Test
    void noAnalystsRoundTripsAsAnEmptyArray() {
        Analysis none = Analysis.queued(
                AnalysisId.newId(),
                new AnalysisSpec(
                        "ZZNONE",
                        LocalDate.of(2026, 9, 25),
                        AssetType.STOCK,
                        List.of(),
                        "deepseek",
                        "a",
                        "b",
                        1,
                        1,
                        "English",
                        false),
                Instant.parse("2026-09-29T10:00:00Z"));

        assertThat(roundTrip(none).spec().analysts()).isEmpty();
    }

    private Analysis inserted(Analysis analysis) {
        repository.insert(analysis);
        return repository.findById(analysis.id()).orElseThrow();
    }

    private static void assertSameButForVersion(Analysis actual, Analysis expected) {
        assertThat(actual.version()).isZero();
        assertThat(actual).usingRecursiveComparison().ignoringFields("version").isEqualTo(expected);
    }

    private Analysis roundTrip(Analysis analysis) {
        repository.insert(analysis);
        return repository.findById(analysis.id()).orElseThrow();
    }

    private static Analysis analysis(
            AnalysisStatus status, AnalysisSource source, Rating rating, AssetType assetType, Analyst analyst) {
        AnalysisSpec spec = new AnalysisSpec(
                "ZZENUM",
                LocalDate.of(2026, 9, 25),
                assetType,
                List.of(analyst),
                "deepseek",
                "a",
                "b",
                1,
                1,
                "English",
                false);
        return new Analysis(
                AnalysisId.newId(),
                spec,
                status,
                source,
                rating,
                null,
                RunStats.EMPTY,
                Instant.parse("2026-09-29T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
