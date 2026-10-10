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
 * V2 adds the optimistic-lock column in place: the presets the test-only migration {@code V1_1} (in
 * {@code persistence/seed} of the test resources) stored before it are read back unchanged, at version 0.
 */
@JpaAdapterTest
@SpringJUnitConfig(V2SettingsVersionMigrationTest.Config.class)
class V2SettingsVersionMigrationTest {

    private static final String PACKAGE_PATH = "classpath:"
            + SettingsPersistenceConfiguration.class.getPackageName().replace('.', '/');

    @Autowired
    private JpaPresetRepositoryAdapter repository;

    @Test
    void existingPresetsKeepEveryValueAndStartAtVersionZero() {
        assertThat(repository.findById(new PresetId("p_seed_a")))
                .contains(new Preset(
                        new PresetId("p_seed_a"),
                        "Cheap DeepSeek",
                        "{\"llmProvider\":\"deepseek\"}",
                        Instant.parse("2026-10-01T10:00:00.5Z"),
                        0L));
        assertThat(repository.findById(new PresetId("p_seed_b")))
                .contains(new Preset(
                        new PresetId("p_seed_b"), "Second", "{}", Instant.parse("2026-10-02T11:30:15.25Z"), 0L));
    }

    @Test
    void aMigratedPresetCanBeSavedAtItsVersion() {
        Preset seeded = repository.findById(new PresetId("p_seed_b")).orElseThrow();

        Preset saved = repository.save(new Preset(seeded.id(), "Renamed", seeded.payload(), null, seeded.version()));

        assertThat(saved.version()).isEqualTo(1);
    }

    // Not a @Configuration, and the scan leaves every @Configuration out: the domain's own Flyway bean in
    // SettingsPersistenceConfiguration and the other tests' configurations. This test's Flyway runs the real
    // migrations with the seed between V1 and V2.
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
                    .locations(PACKAGE_PATH + "/migration", PACKAGE_PATH + "/seed")
                    .load();
        }
    }
}
