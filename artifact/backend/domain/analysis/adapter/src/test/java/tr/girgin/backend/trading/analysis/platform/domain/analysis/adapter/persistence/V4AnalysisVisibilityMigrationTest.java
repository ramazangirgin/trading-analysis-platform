package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;
import tr.girgin.backend.trading.analysis.platform.library.mapper.DurationToMillisMapper;
import tr.girgin.backend.trading.analysis.platform.library.persistence.JpaAdapterTest;

/**
 * V4 adds the visibility in place: the analyses the test-only migration {@code V1_1} (in {@code persistence/seed}
 * of the test resources) stored before it are private after it, with no change time.
 */
@JpaAdapterTest
@SpringJUnitConfig(V4AnalysisVisibilityMigrationTest.Config.class)
class V4AnalysisVisibilityMigrationTest {

    private static final String PACKAGE_PATH = "classpath:"
            + AnalysisPersistenceConfiguration.class.getPackageName().replace('.', '/');

    @Autowired
    private JpaAnalysisRepositoryAdapter repository;

    @Test
    void rowsFromBeforeV4ArePrivateWithNoChangeTime() {
        Analysis platformRun = repository.findById(new AnalysisId("r_seed_a")).orElseThrow();
        Analysis imported =
                repository.findByExternalRef("report:AAPL/2026-09-27").orElseThrow();

        assertThat(platformRun.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(platformRun.visibilityChangedAt()).isNull();
        assertThat(imported.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(imported.visibilityChangedAt()).isNull();
    }

    // Not a @Configuration, as in V3AnalysisOwnerMigrationTest: this test's Flyway runs the real migrations
    // with the seed between V1 and V2.
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
