package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.library.persistence.JpaAdapterTest;

/**
 * V4 adds the owner column in place: the preset the test-only migration {@code V3_1} (in {@code persistence/seedowner}
 * of the test resources) stored before it is read back unchanged, without an owner.
 */
@JpaAdapterTest
@SpringJUnitConfig(V4SettingsOwnerMigrationTest.Config.class)
class V4SettingsOwnerMigrationTest {

    private static final String PACKAGE_PATH = "classpath:"
            + SettingsPersistenceConfiguration.class.getPackageName().replace('.', '/');

    @Autowired
    private JpaPresetRepositoryAdapter repository;

    @Test
    void anExistingPresetKeepsEveryValueAndHasNoOwner() {
        assertThat(repository.findById(new PresetId("p_seed_owner")))
                .contains(new Preset(
                        new PresetId("p_seed_owner"),
                        "Before owners",
                        "{\"llmProvider\":\"deepseek\"}",
                        null,
                        Instant.parse("2026-10-03T09:15:00Z"),
                        3L));
    }

    // Not a @Configuration, for the reason given in V2SettingsVersionMigrationTest.
    @AutoConfigurationPackage
    @ComponentScan(
            basePackageClasses = JpaPresetRepositoryAdapter.class,
            excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class Config {

        @Bean(initMethod = "migrate")
        Flyway settingsFlyway(DataSource dataSource) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(SettingsPersistenceConfiguration.SCHEMA)
                    .createSchemas(true)
                    .table("FLYWAY_SCHEMA_HISTORY")
                    .locations(PACKAGE_PATH + "/migration", PACKAGE_PATH + "/seedowner")
                    .load();
        }
    }
}
