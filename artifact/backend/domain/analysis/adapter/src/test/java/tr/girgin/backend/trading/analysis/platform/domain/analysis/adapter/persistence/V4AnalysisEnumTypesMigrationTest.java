package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
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
import tr.girgin.backend.trading.analysis.platform.library.persistence.PostgresTestDatabase;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestMigrations;

/**
 * V4 converts an existing database in place: the rows V1 stored as text (seeded by the test-only
 * migration {@code V1_1} in {@code db/seed/analysis}) are read back unchanged through the repository.
 */
@JpaAdapterTest
@SpringJUnitConfig(V4AnalysisEnumTypesMigrationTest.Config.class)
class V4AnalysisEnumTypesMigrationTest {

    @Autowired
    private JpaAnalysisRepositoryAdapter repository;

    @Test
    void aFinishedRunKeepsEveryValueAndTheAnalystsWithASpace() {
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
                "4242");

        assertThat(repository.findById(new AnalysisId("r_seed_a"))).contains(expected);
    }

    @Test
    void anEmptyAnalystStringBecomesNoAnalystsAndNoRatingStaysNull() {
        Analysis found = repository.findById(new AnalysisId("r_seed_b")).orElseThrow();

        assertThat(found.spec().analysts()).isEmpty();
        assertThat(found.spec().assetType()).isEqualTo(AssetType.CRYPTO);
        assertThat(found.status()).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(found.rating()).isNull();
    }

    @Test
    void anImportedRunKeepsItsSourceRatingAndAllAnalysts() {
        Analysis found = repository.findByExternalRef("report:AAPL/2026-09-27").orElseThrow();

        assertThat(found.id()).isEqualTo(new AnalysisId("r_seed_c"));
        assertThat(found.source()).isEqualTo(AnalysisSource.EXTERNAL);
        assertThat(found.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(found.rating()).isEqualTo(Rating.REVIEW);
        assertThat(found.spec().analysts()).containsExactly(Analyst.values());
    }

    // Not a @Configuration: other tests in this module scan the whole adapter package, and must not
    // pick up this data source.
    @AutoConfigurationPackage
    @ComponentScan(basePackageClasses = {JpaAnalysisRepositoryAdapter.class, DurationToMillisMapper.class})
    static class Config {

        @Bean
        DataSource dataSource() {
            DataSource dataSource = PostgresTestDatabase.create().dataSource();
            TestMigrations.migrate(dataSource, null, "classpath:db/seed/analysis");
            return dataSource;
        }
    }
}
