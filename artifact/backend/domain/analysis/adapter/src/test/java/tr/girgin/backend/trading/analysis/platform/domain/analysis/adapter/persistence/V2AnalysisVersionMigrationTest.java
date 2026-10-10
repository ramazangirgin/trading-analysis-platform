package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;
import tr.girgin.backend.trading.analysis.platform.library.mapper.DurationToMillisMapper;
import tr.girgin.backend.trading.analysis.platform.library.persistence.JpaAdapterTest;

/**
 * V2 adds the optimistic-lock column in place: the analyses the test-only migration {@code V1_1} (in
 * {@code persistence/seed} of the test resources) stored before it are read back unchanged, at version 0.
 */
@JpaAdapterTest
@SpringJUnitConfig(V2AnalysisVersionMigrationTest.Config.class)
class V2AnalysisVersionMigrationTest {

    private static final String PACKAGE_PATH = "classpath:"
            + AnalysisPersistenceConfiguration.class.getPackageName().replace('.', '/');

    @Autowired
    private JpaAnalysisRepositoryAdapter repository;

    @Test
    void aFinishedRunKeepsEveryValueAndStartsAtVersionZero() {
        Analysis expected = new Analysis(
                new AnalysisId("r_seed_a"),
                new AnalysisSpec(
                        "NVDA",
                        LocalDate.of(2026, 9, 25),
                        AssetType.STOCK,
                        List.of(Analyst.NEWS, Analyst.MARKET),
                        "deepseek",
                        "deepseek-v4-pro",
                        "deepseek-v4-flash",
                        1,
                        2,
                        "Turkish",
                        true),
                AnalysisStatus.COMPLETED,
                AnalysisSource.PLATFORM,
                Rating.OVERWEIGHT,
                "Rating: Overweight",
                new RunStats(12, 10, 77137, 48090, new BigDecimal("0.42"), Duration.ofMillis(260_500)),
                Instant.parse("2026-09-29T10:00:00.123Z"),
                Instant.parse("2026-09-29T10:00:01Z"),
                Instant.parse("2026-09-29T10:04:21Z"),
                null,
                null,
                null,
                "4242",
                0L);

        assertThat(repository.findById(new AnalysisId("r_seed_a"))).contains(expected);
    }

    @Test
    void aMigratedImportCanBeReplacedAtItsVersion() {
        Analysis seeded = repository.findByExternalRef("report:AAPL/2026-09-27").orElseThrow();

        Analysis replaced = repository.replaceImported(seeded.withDecision(Rating.HOLD, "Rating: Hold"));

        assertThat(seeded.version()).isZero();
        assertThat(replaced.version()).isEqualTo(1);
        assertThat(replaced.rating()).isEqualTo(Rating.HOLD);
    }

    // Not a @Configuration, and the scan leaves every @Configuration out: the domain's own Flyway bean in
    // AnalysisPersistenceConfiguration and the other tests' configurations. This test's Flyway runs the real
    // migrations with the seed between V1 and V2.
    @AutoConfigurationPackage
    @ComponentScan(
            basePackageClasses = {JpaAnalysisRepositoryAdapter.class, DurationToMillisMapper.class},
            excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class Config {

        @Bean(initMethod = "migrate")
        Flyway analysisFlyway(DataSource dataSource) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(AnalysisPersistenceConfiguration.SCHEMA)
                    .createSchemas(true)
                    .table("FLYWAY_SCHEMA_HISTORY")
                    .locations(PACKAGE_PATH + "/migration", PACKAGE_PATH + "/seed")
                    .load();
        }
    }
}
