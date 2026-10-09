package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.library.persistence.JpaAdapterTest;
import tr.girgin.backend.trading.analysis.platform.library.persistence.PostgresTestDatabase;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestMigrations;

/**
 * V7 adds the optimistic-lock column in place: the presets the test-only migration {@code V2_1} (in
 * {@code db/seed/settings}) stored before it are read back unchanged, at version 0.
 */
@JpaAdapterTest
@SpringJUnitConfig(V7SettingsVersionMigrationTest.Config.class)
class V7SettingsVersionMigrationTest {

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

    // Not a @Configuration: JpaPresetRepositoryAdapterTest scans this package, and must not pick up this
    // data source.
    @AutoConfigurationPackage
    @ComponentScan(basePackageClasses = JpaPresetRepositoryAdapter.class)
    static class Config {

        @Bean
        DataSource dataSource() {
            DataSource dataSource = PostgresTestDatabase.create().dataSource();
            // Only this domain's migrations: V1 belongs to another module.
            TestMigrations.migrate(dataSource, "1", "classpath:db/seed/settings");
            return dataSource;
        }
    }
}
