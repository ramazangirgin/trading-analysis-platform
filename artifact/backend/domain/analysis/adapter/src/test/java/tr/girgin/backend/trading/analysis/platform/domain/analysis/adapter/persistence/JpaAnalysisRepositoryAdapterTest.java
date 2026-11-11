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
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.TestAnalyses;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

class JpaAnalysisRepositoryAdapterTest extends AdapterTestSupport {

    private static final int LIST_LIMIT = 500;
    private static final AnalysisFilter ALL = new AnalysisFilter(null, null, null, Set.of(), null);
    private static final OwnerId OWNER = new OwnerId("zz-owner");
    private static final OwnerId OTHER_OWNER = new OwnerId("zz-other-owner");

    @Autowired
    private JpaAnalysisRepositoryAdapter jpaAnalysisRepositoryAdapter;

    @Test
    void roundTripsAnAnalysisThroughEveryTransition() {
        Instant created = Instant.parse("2026-09-29T10:00:00.123Z");
        Analysis queued = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("NVDA"), OWNER, created));

        Analysis running = TestAnalyses.running(queued, created.plusSeconds(1), "4242@1790000000000");
        Analysis withStats = TestAnalyses.withStats(
                running, new RunStats(12, 10, 77137, 48090, new BigDecimal("0.42"), Duration.ofMillis(260_500)));
        Analysis done = TestAnalyses.finished(
                TestAnalyses.withDecision(withStats, Rating.OVERWEIGHT, "Rating: Overweight"),
                AnalysisStatus.COMPLETED,
                created.plusSeconds(261),
                null,
                null);
        Analysis updated = jpaAnalysisRepositoryAdapter.update(done);

        assertThat(updated.version()).isEqualTo(1);
        assertThat(jpaAnalysisRepositoryAdapter.findById(queued.id())).contains(updated);
        assertThat(updated).usingRecursiveComparison().ignoringFields("version").isEqualTo(done);
        assertThat(updated.spec()).isEqualTo(queued.spec());
    }

    @Test
    void roundTripsAnElapsedDurationAndAFractionOfACentCostExactly() {
        Instant created = Instant.parse("2026-09-29T10:00:00Z");
        Analysis queued = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZSTATS"), OWNER, created));
        RunStats stats = new RunStats(1, 1, 1, 1, new BigDecimal("0.000123"), Duration.parse("PT4M21.5S"));

        jpaAnalysisRepositoryAdapter.update(TestAnalyses.withStats(queued, stats));

        RunStats found =
                jpaAnalysisRepositoryAdapter.findById(queued.id()).orElseThrow().stats();
        assertThat(found.elapsed()).isEqualTo(Duration.parse("PT4M21.5S"));
        assertThat(found.costUsd()).isEqualByComparingTo("0.000123");
    }

    @Test
    void aNewRecordStartsAtVersionZeroAndEveryWriteAddsOne() {
        Analysis queued = TestAnalyses.queued(TestAnalyses.newId(), spec("ZZVER"), OWNER, Instant.now());
        assertThat(queued.version()).isNull();
        jpaAnalysisRepositoryAdapter.insert(queued);
        Analysis stored = jpaAnalysisRepositoryAdapter.findById(queued.id()).orElseThrow();
        assertThat(stored.version()).isZero();

        Analysis first = jpaAnalysisRepositoryAdapter.update(TestAnalyses.running(stored, Instant.now(), "1"));
        Analysis second =
                jpaAnalysisRepositoryAdapter.update(TestAnalyses.withDecision(first, Rating.HOLD, "Rating: Hold"));

        assertThat(first.version()).isEqualTo(1);
        assertThat(second.version()).isEqualTo(2);
        assertThat(jpaAnalysisRepositoryAdapter
                        .findById(queued.id())
                        .orElseThrow()
                        .version())
                .isEqualTo(2);
    }

    @Test
    void aStaleUpdateFailsAndLeavesTheNewerRowUnchanged() {
        // Millisecond instants: the column keeps microseconds, a Linux clock has nanoseconds.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Analysis stored = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZSTALE"), OWNER, now));
        Analysis firstCopy = jpaAnalysisRepositoryAdapter.findById(stored.id()).orElseThrow();
        Analysis staleCopy = jpaAnalysisRepositoryAdapter.findById(stored.id()).orElseThrow();
        Analysis firstWrite = jpaAnalysisRepositoryAdapter.update(TestAnalyses.running(firstCopy, now, "first"));

        assertThatThrownBy(() -> jpaAnalysisRepositoryAdapter.update(TestAnalyses.running(staleCopy, now, "second")))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(jpaAnalysisRepositoryAdapter.findById(stored.id())).contains(firstWrite);
    }

    @Test
    void aStaleReplaceImportedFailsAndLeavesTheNewerRowUnchanged() {
        Instant ended = Instant.parse("2026-09-27T21:34:35.383Z");
        Analysis stored = inserted(newImport(OWNER, "ZZSTALEIMP", ended));
        Analysis staleCopy = jpaAnalysisRepositoryAdapter.findById(stored.id()).orElseThrow();
        Analysis firstWrite = jpaAnalysisRepositoryAdapter.replaceImported(imported(
                stored.id(),
                stored.version(),
                OWNER,
                "ZZSTALEIMP",
                List.of(Analyst.NEWS),
                Rating.HOLD,
                "Hold",
                null,
                ended));

        assertThatThrownBy(() -> jpaAnalysisRepositoryAdapter.replaceImported(imported(
                        staleCopy.id(),
                        staleCopy.version(),
                        OWNER,
                        "ZZSTALEIMP",
                        List.of(Analyst.MARKET),
                        Rating.SELL,
                        "Sell",
                        null,
                        ended)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(jpaAnalysisRepositoryAdapter.findById(stored.id())).contains(firstWrite);
    }

    @Test
    void aRecordWithoutAVersionCannotUpdateAStoredRow() {
        Analysis queued = TestAnalyses.queued(TestAnalyses.newId(), spec("ZZNULLVER"), OWNER, Instant.now());
        jpaAnalysisRepositoryAdapter.insert(queued);

        assertThatThrownBy(() -> jpaAnalysisRepositoryAdapter.update(TestAnalyses.running(queued, Instant.now(), "x")))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void keepsTheTradeDateAndAllThreeTimestampsInTheirNativeTypes() {
        Instant created = Instant.parse("2026-09-29T23:59:59.123456Z");
        Analysis queued = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZTYPES"), OWNER, created));
        Analysis done = TestAnalyses.finished(
                TestAnalyses.running(queued, created.plusNanos(1_000), "4244"),
                AnalysisStatus.COMPLETED,
                created.plusSeconds(90).plusNanos(2_000),
                null,
                null);
        jpaAnalysisRepositoryAdapter.update(done);

        Analysis found = jpaAnalysisRepositoryAdapter.findById(queued.id()).orElseThrow();

        assertThat(found.createdAt()).isEqualTo(created);
        assertThat(found.startedAt()).isEqualTo(created.plusNanos(1_000));
        assertThat(found.endedAt()).isEqualTo(created.plusSeconds(90).plusNanos(2_000));
        assertThat(found.spec().tradeDate()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void listsNewestFirstAndFilters() {
        Instant base = Instant.parse("2026-09-29T11:00:00Z");
        Analysis older = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZOLD"), OWNER, base));
        Analysis newer = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZNEW"), OWNER, base.plusMillis(1)));
        newer = jpaAnalysisRepositoryAdapter.update(TestAnalyses.running(newer, base, "4243"));

        List<Analysis> all = jpaAnalysisRepositoryAdapter.findAll(ALL);
        assertThat(all.indexOf(newer)).isLessThan(all.indexOf(older));
        assertThat(jpaAnalysisRepositoryAdapter.findAll(new AnalysisFilter(null, "ZZOLD", null, Set.of(), null)))
                .containsExactly(older);
        assertThat(jpaAnalysisRepositoryAdapter.findAll(
                        new AnalysisFilter(AnalysisStatus.RUNNING, "ZZNEW", null, Set.of(), null)))
                .containsExactly(newer);
        assertThat(jpaAnalysisRepositoryAdapter.findByStatusIn(Set.of(AnalysisStatus.QUEUED)))
                .contains(older)
                .doesNotContain(newer);
        assertThat(jpaAnalysisRepositoryAdapter.findByStatusIn(Set.of())).isEmpty();
    }

    @Test
    void anUpdateAndAReplaceImportedKeepTheStoredOwner() {
        Instant ended = Instant.parse("2026-09-27T21:34:35.383Z");
        Analysis run = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZOWNUPD"), OWNER, ended));
        Analysis imported = inserted(newImport(OWNER, "ZZOWNIMP", ended));

        Analysis updated = jpaAnalysisRepositoryAdapter.update(TestAnalyses.finished(
                TestAnalyses.running(run, ended, "1"), AnalysisStatus.STOPPED, ended, null, null));
        Analysis replaced = jpaAnalysisRepositoryAdapter.replaceImported(imported(
                imported.id(),
                imported.version(),
                OTHER_OWNER,
                "ZZOWNIMP",
                List.of(Analyst.NEWS),
                Rating.HOLD,
                "Hold",
                null,
                ended));

        assertThat(updated.owner()).isEqualTo(OWNER);
        assertThat(replaced.owner()).isEqualTo(OWNER);
        assertThat(jpaAnalysisRepositoryAdapter
                        .findById(replaced.id())
                        .orElseThrow()
                        .owner())
                .isEqualTo(OWNER);
    }

    @Test
    void storesTheVisibilityAndItsTimeAndRaisesTheVersion() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Analysis stored = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZVIS"), OWNER, now));
        assertThat(stored.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(stored.visibilityChangedAt()).isNull();

        Analysis shared = jpaAnalysisRepositoryAdapter.update(
                TestAnalyses.withVisibility(stored, Visibility.PUBLIC, now.plusSeconds(5)));

        assertThat(shared.version()).isEqualTo(stored.version() + 1);
        assertThat(shared.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(shared.visibilityChangedAt()).isEqualTo(now.plusSeconds(5));
        assertThat(jpaAnalysisRepositoryAdapter.findById(stored.id())).contains(shared);
    }

    @Test
    void aStaleVisibilityUpdateFailsAndLeavesTheNewerRowUnchanged() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Analysis stored = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZVISSTALE"), OWNER, now));
        Analysis staleCopy = jpaAnalysisRepositoryAdapter.findById(stored.id()).orElseThrow();
        Analysis firstWrite = jpaAnalysisRepositoryAdapter.update(TestAnalyses.running(stored, now, "first"));

        assertThatThrownBy(() -> jpaAnalysisRepositoryAdapter.update(
                        TestAnalyses.withVisibility(staleCopy, Visibility.PUBLIC, now)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(jpaAnalysisRepositoryAdapter.findById(stored.id())).contains(firstWrite);
    }

    @Test
    void aRunUpdateAndAReplaceImportedKeepTheStoredVisibility() {
        Instant ended = Instant.parse("2026-09-27T21:34:35.383Z");
        Analysis run = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZVISUPD"), OWNER, ended));
        Analysis imported = inserted(newImport(OWNER, "ZZVISIMP", ended));
        Analysis sharedRun =
                jpaAnalysisRepositoryAdapter.update(TestAnalyses.withVisibility(run, Visibility.PUBLIC, ended));
        Analysis sharedImport =
                jpaAnalysisRepositoryAdapter.update(TestAnalyses.withVisibility(imported, Visibility.PUBLIC, ended));

        Analysis updated = jpaAnalysisRepositoryAdapter.update(TestAnalyses.finished(
                TestAnalyses.running(sharedRun, ended, "1"), AnalysisStatus.COMPLETED, ended, null, null));
        Analysis replaced = jpaAnalysisRepositoryAdapter.replaceImported(imported(
                sharedImport.id(),
                sharedImport.version(),
                OWNER,
                "ZZVISIMP",
                List.of(Analyst.NEWS),
                Rating.HOLD,
                "Hold",
                null,
                ended));

        assertThat(updated.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(updated.visibilityChangedAt()).isEqualTo(ended);
        assertThat(replaced.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(replaced.visibilityChangedAt()).isEqualTo(ended);
    }

    @Test
    void filtersByVisibilityAndByNotOwnedBy() {
        Instant base = Instant.parse("2026-09-29T14:00:00Z");
        Analysis mineOpen = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZVISF"), OWNER, base));
        mineOpen = jpaAnalysisRepositoryAdapter.update(TestAnalyses.withVisibility(mineOpen, Visibility.PUBLIC, base));
        Analysis theirsOpen = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZVISF"), OTHER_OWNER, base));
        theirsOpen =
                jpaAnalysisRepositoryAdapter.update(TestAnalyses.withVisibility(theirsOpen, Visibility.PUBLIC, base));
        Analysis theirsClosed = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZVISF"), OTHER_OWNER, base));
        Analysis nobodys = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZVISF"), null, base));

        assertThat(jpaAnalysisRepositoryAdapter.findAll(
                        new AnalysisFilter(null, "ZZVISF", null, Set.of(Visibility.PUBLIC), null)))
                .containsExactlyInAnyOrder(mineOpen, theirsOpen);
        assertThat(jpaAnalysisRepositoryAdapter.findAll(
                        new AnalysisFilter(null, "ZZVISF", null, Set.of(Visibility.PUBLIC), OWNER)))
                .containsExactly(theirsOpen);
        assertThat(jpaAnalysisRepositoryAdapter.findAll(new AnalysisFilter(null, "ZZVISF", null, Set.of(), OWNER)))
                .containsExactlyInAnyOrder(theirsOpen, theirsClosed, nobodys);
    }

    @Test
    void listIsLimitedToFiveHundredNewest() {
        Instant base = Instant.parse("2030-01-01T00:00:00Z");
        List<Analysis> inserted = new ArrayList<>();
        for (int i = 0; i <= LIST_LIMIT; i++) {
            inserted.add(
                    inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZLIMIT"), OWNER, base.plusSeconds(i))));
        }

        List<Analysis> listed = jpaAnalysisRepositoryAdapter.findAll(ALL);

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
        Analysis imported = inserted(newImport(OWNER, "ZZIMP", ended));

        Analysis replaced = jpaAnalysisRepositoryAdapter.replaceImported(imported(
                imported.id(),
                imported.version(),
                OWNER,
                "ZZIMP",
                List.of(Analyst.MARKET),
                Rating.HOLD,
                "Hold",
                run,
                ended));

        assertThat(replaced.version()).isEqualTo(1);
        assertThat(jpaAnalysisRepositoryAdapter.findByExternalRef("report:ZZIMP/2026-09-27"))
                .contains(replaced);
        assertThat(jpaAnalysisRepositoryAdapter.findByExternalRef("run:zz-a2cd"))
                .isEmpty();
    }

    @Test
    void platformRunsAreNeverReplacedAsImported() {
        Analysis queued = TestAnalyses.queued(TestAnalyses.newId(), spec("NVDA"), OWNER, Instant.now());
        jpaAnalysisRepositoryAdapter.insert(queued);

        assertThatThrownBy(() -> jpaAnalysisRepositoryAdapter.replaceImported(queued))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updatingAMissingAnalysisFails() {
        Analysis ghost = TestAnalyses.queued(TestAnalyses.newId(), spec("NVDA"), OWNER, Instant.now());

        assertThatThrownBy(() -> jpaAnalysisRepositoryAdapter.update(ghost)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateKeepsTheSpecAndCreationTime() {
        Instant created = Instant.parse("2026-09-29T10:00:00Z");
        Analysis queued = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZKEEP"), OWNER, created));
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
                OTHER_OWNER,
                Visibility.PUBLIC,
                created,
                queued.version());

        jpaAnalysisRepositoryAdapter.update(changed);

        Analysis found = jpaAnalysisRepositoryAdapter.findById(queued.id()).orElseThrow();
        assertThat(found.owner()).isEqualTo(OWNER);
        assertThat(found.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(found.visibilityChangedAt()).isEqualTo(created);
        assertThat(found.spec()).isEqualTo(queued.spec());
        assertThat(found.createdAt()).isEqualTo(created);
        assertThat(found.status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(found.runnerRef()).isEqualTo("4245");
    }

    @Test
    void insertingAnExistingIdFails() {
        Analysis queued = TestAnalyses.queued(TestAnalyses.newId(), spec("NVDA"), OWNER, Instant.now());
        jpaAnalysisRepositoryAdapter.insert(queued);

        assertThatThrownBy(() -> jpaAnalysisRepositoryAdapter.insert(queued)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void unknownIdsAreEmpty() {
        assertThat(jpaAnalysisRepositoryAdapter.findById(new AnalysisId("r_nope")))
                .isEmpty();
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
        for (Visibility visibility : Visibility.values()) {
            Analysis analysis = TestAnalyses.withVisibility(
                    analysis(
                            AnalysisStatus.COMPLETED,
                            AnalysisSource.PLATFORM,
                            Rating.HOLD,
                            AssetType.STOCK,
                            Analyst.MARKET),
                    visibility,
                    Instant.EPOCH);
            assertThat(roundTrip(analysis).visibility()).isEqualTo(visibility);
        }
        for (Analyst analyst : Analyst.values()) {
            Analysis analysis =
                    analysis(AnalysisStatus.QUEUED, AnalysisSource.PLATFORM, null, AssetType.STOCK, analyst);
            assertThat(roundTrip(analysis).spec().analysts()).containsExactly(analyst);
        }
        Analysis everyAnalyst = TestAnalyses.queued(
                TestAnalyses.newId(),
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
                OWNER,
                Instant.parse("2026-09-29T10:00:00Z"));
        assertSameButForVersion(roundTrip(everyAnalyst), everyAnalyst);
    }

    @Test
    void noAnalystsRoundTripsAsAnEmptyArray() {
        Analysis none = TestAnalyses.queued(
                TestAnalyses.newId(),
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
                OWNER,
                Instant.parse("2026-09-29T10:00:00Z"));

        assertThat(roundTrip(none).spec().analysts()).isEmpty();
    }

    private static Analysis newImport(OwnerId owner, String ticker, Instant ended) {
        return imported(TestAnalyses.newId(), null, owner, ticker, List.of(), null, null, null, ended);
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // one value per field of an imported record
    private static Analysis imported(
            AnalysisId id,
            Long version,
            OwnerId owner,
            String ticker,
            List<Analyst> analysts,
            Rating rating,
            String decision,
            ExternalRun run,
            Instant ended) {
        LocalDate tradeDate = LocalDate.of(2026, 9, 27);
        var origin = ExternalAnalysis.Origin.REPORT_FILES;
        var external = new ExternalAnalysis(origin, ticker, tradeDate, analysts, rating, decision, run, ended);
        return TestAnalyses.importedFromReportFiles(id, version, owner, external);
    }

    private Analysis inserted(Analysis analysis) {
        jpaAnalysisRepositoryAdapter.insert(analysis);
        return jpaAnalysisRepositoryAdapter.findById(analysis.id()).orElseThrow();
    }

    private static void assertSameButForVersion(Analysis actual, Analysis expected) {
        assertThat(actual.version()).isZero();
        assertThat(actual).usingRecursiveComparison().ignoringFields("version").isEqualTo(expected);
    }

    private Analysis roundTrip(Analysis analysis) {
        jpaAnalysisRepositoryAdapter.insert(analysis);
        return jpaAnalysisRepositoryAdapter.findById(analysis.id()).orElseThrow();
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
                TestAnalyses.newId(),
                spec,
                status,
                source,
                rating,
                null,
                new RunStats(0, 0, 0, 0, null, Duration.ZERO),
                Instant.parse("2026-09-29T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                OWNER,
                Visibility.PRIVATE,
                null,
                null);
    }
}
