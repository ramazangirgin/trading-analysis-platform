package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
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
import tr.girgin.backend.trading.analysis.platform.library.mapper.DurationToMillisMapper;
import tr.girgin.backend.trading.analysis.platform.library.persistence.JpaAdapterTest;

/** V6 turns the elapsed milliseconds into an interval and the cost into a numeric: rows written at V5 keep both. */
@JpaAdapterTest
@SpringJUnitConfig(V6AnalysisColumnTypesMigrationTest.Config.class)
class V6AnalysisColumnTypesMigrationTest {

    private static final String PACKAGE_PATH = "classpath:"
            + AnalysisPersistenceConfiguration.class.getPackageName().replace('.', '/');

    @Autowired
    private JpaAnalysisRepositoryAdapter repository;

    @Test
    void elapsedMillisecondsAndCostWrittenAtV5ReadTheSameAfterV6() {
        Analysis costed = repository.findById(new AnalysisId("r_v5_costed")).orElseThrow();

        assertThat(costed.stats().elapsed()).isEqualTo(Duration.ofMillis(261_500));
        assertThat(costed.stats().costUsd()).isEqualByComparingTo("0.0277");
    }

    @Test
    void aNullCostStaysNull() {
        Analysis free = repository.findById(new AnalysisId("r_v5_free")).orElseThrow();

        assertThat(free.stats().costUsd()).isNull();
        assertThat(free.stats().elapsed()).isEqualTo(Duration.ZERO);
    }

    // Not a @Configuration, as in the other migration tests: this Flyway stops at V5 to write rows the way V5
    // left them, then runs the rest.
    @AutoConfigurationPackage
    @ComponentScan(
            basePackageClasses = {JpaAnalysisRepositoryAdapter.class, DurationToMillisMapper.class},
            excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class Config {

        @Bean
        Flyway analysisFlyway(DataSource dataSource) throws SQLException {
            Flyway atV5 = flyway(dataSource).target("5").load();
            atV5.migrate();
            insertRowsAtV5(dataSource);
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

        private static void insertRowsAtV5(DataSource dataSource) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(row("r_v5_costed", "0.0277", "261500"));
                statement.executeUpdate(row("r_v5_free", "NULL", "0"));
            }
        }

        private static String row(String id, String cost, String elapsedMs) {
            return """
                    INSERT INTO "ANALYSIS"."ANALYSES" ("ID", "TICKER", "TRADE_DATE", "ASSET_TYPE", "ANALYSTS",
                        "LLM_PROVIDER", "DEEP_THINK_LLM", "QUICK_THINK_LLM", "MAX_DEBATE_ROUNDS",
                        "MAX_RISK_DISCUSS_ROUNDS", "OUTPUT_LANGUAGE", "CHECKPOINT_ENABLED", "STATUS", "SOURCE",
                        "RATING", "DECISION", "LLM_CALLS", "TOOL_CALLS", "TOKENS_IN", "TOKENS_OUT", "COST_USD",
                        "ELAPSED_MS", "CREATED_AT", "STARTED_AT", "ENDED_AT", "OWNER_ID", "VISIBILITY",
                        "VISIBILITY_CHANGED_AT")
                    VALUES ('%s', 'NVDA', '2026-09-25', 'STOCK', '{MARKET}', 'deepseek', 'a', 'b', 1, 1,
                        'English', FALSE, 'COMPLETED', 'PLATFORM', 'HOLD', 'Rating: Hold', 1, 1, 1, 1, %s, %s,
                        '2026-09-29 10:00:00+00', '2026-09-29 10:00:01+00', '2026-09-29 10:04:21+00', 'owner',
                        'PRIVATE', NULL)
                    """.formatted(id, cost, elapsedMs);
        }
    }
}
