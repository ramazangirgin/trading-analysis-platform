package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
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

/** V5 appends a value to the visibility type: rows written at V4 read back the same, and the new value stores. */
@JpaAdapterTest
@SpringJUnitConfig(V5AnalysisPartialVisibilityMigrationTest.Config.class)
class V5AnalysisPartialVisibilityMigrationTest {

    private static final String PACKAGE_PATH = "classpath:"
            + AnalysisPersistenceConfiguration.class.getPackageName().replace('.', '/');

    @Autowired
    private JpaAnalysisRepositoryAdapter repository;

    @Test
    void rowsWrittenAtV4ReadTheSameAfterV5() {
        Analysis privateRun =
                repository.findById(new AnalysisId("r_v4_private")).orElseThrow();
        Analysis publicRun = repository.findById(new AnalysisId("r_v4_public")).orElseThrow();

        assertThat(privateRun.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(privateRun.visibilityChangedAt()).isNull();
        assertThat(publicRun.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(publicRun.visibilityChangedAt()).isEqualTo(Instant.parse("2026-10-01T08:00:00Z"));
    }

    @Test
    void aRowCanBecomePartialAfterV5() {
        Analysis privateRun =
                repository.findById(new AnalysisId("r_v4_private")).orElseThrow();

        Analysis partial = repository.update(privateRun.withVisibility(Visibility.PARTIAL, Instant.EPOCH));

        assertThat(repository.findById(partial.id()).orElseThrow().visibility()).isEqualTo(Visibility.PARTIAL);
    }

    // Not a @Configuration, as in the other migration tests: this Flyway stops at V4 to write rows the way V4
    // left them, then runs the rest.
    @AutoConfigurationPackage
    @ComponentScan(
            basePackageClasses = {JpaAnalysisRepositoryAdapter.class, DurationToMillisMapper.class},
            excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class Config {

        @Bean
        Flyway analysisFlyway(DataSource dataSource) throws SQLException {
            Flyway atV4 = flyway(dataSource).target("4").load();
            atV4.migrate();
            insertRowsAtV4(dataSource);
            Flyway latest = flyway(dataSource).load();
            latest.migrate();
            return latest;
        }

        private static FluentConfiguration flyway(DataSource dataSource) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(AnalysisPersistenceConfiguration.SCHEMA)
                    .createSchemas(true)
                    .table("FLYWAY_SCHEMA_HISTORY")
                    .locations(PACKAGE_PATH + "/migration");
        }

        private static void insertRowsAtV4(DataSource dataSource) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(row("r_v4_private", "'PRIVATE'", "NULL"));
                statement.executeUpdate(row("r_v4_public", "'PUBLIC'", "'2026-10-01 08:00:00+00'"));
            }
        }

        private static String row(String id, String visibility, String changedAt) {
            return """
                    INSERT INTO "ANALYSIS"."ANALYSES" ("ID", "TICKER", "TRADE_DATE", "ASSET_TYPE", "ANALYSTS",
                        "LLM_PROVIDER", "DEEP_THINK_LLM", "QUICK_THINK_LLM", "MAX_DEBATE_ROUNDS",
                        "MAX_RISK_DISCUSS_ROUNDS", "OUTPUT_LANGUAGE", "CHECKPOINT_ENABLED", "STATUS", "SOURCE",
                        "RATING", "DECISION", "LLM_CALLS", "TOOL_CALLS", "TOKENS_IN", "TOKENS_OUT", "COST_USD",
                        "ELAPSED_MS", "CREATED_AT", "STARTED_AT", "ENDED_AT", "OWNER_ID", "VISIBILITY",
                        "VISIBILITY_CHANGED_AT")
                    VALUES ('%s', 'NVDA', '2026-09-25', 'STOCK', '{MARKET}', 'deepseek', 'a', 'b', 1, 1,
                        'English', FALSE, 'COMPLETED', 'PLATFORM', 'HOLD', 'Rating: Hold', 1, 1, 1, 1, 0.1, 1000,
                        '2026-09-29 10:00:00+00', '2026-09-29 10:00:01+00', '2026-09-29 10:04:21+00', 'owner',
                        %s, %s)
                    """.formatted(id, visibility, changedAt);
        }
    }
}
