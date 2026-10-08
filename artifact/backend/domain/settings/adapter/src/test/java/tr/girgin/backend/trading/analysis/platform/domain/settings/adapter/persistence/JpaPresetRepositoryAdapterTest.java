package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import javax.sql.DataSource;
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
import tr.girgin.backend.trading.analysis.platform.library.persistence.PostgresTestDatabase;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestMigrations;

@JpaAdapterTest
@SpringJUnitConfig(JpaPresetRepositoryAdapterTest.Config.class)
class JpaPresetRepositoryAdapterTest {

    @Autowired
    private JpaPresetRepositoryAdapter repository;

    @Test
    void savesUpdatesAndDeletes() {
        Preset preset = new Preset(
                PresetId.newId(),
                "Cheap DeepSeek",
                "{\"llmProvider\":\"deepseek\"}",
                Instant.parse("2026-09-29T10:00:00Z"));
        repository.save(preset);
        Preset renamed =
                new Preset(preset.id(), "DeepSeek flash", preset.payload(), Instant.parse("2026-09-29T11:00:00Z"));

        repository.save(renamed);

        assertThat(repository.findById(preset.id())).contains(renamed);
        assertThat(repository.findAll()).containsExactly(renamed);
        assertThat(repository.delete(preset.id())).isTrue();
        assertThat(repository.delete(preset.id())).isFalse();
        assertThat(repository.findAll()).isEmpty();
    }

    @Configuration
    @AutoConfigurationPackage
    @ComponentScan(basePackageClasses = JpaPresetRepositoryAdapterTest.class)
    static class Config {

        @Bean
        DataSource dataSource() {
            DataSource dataSource = PostgresTestDatabase.create().dataSource();
            // Only this domain's migration: V1 belongs to another module.
            TestMigrations.migrate(dataSource, "1");
            return dataSource;
        }
    }
}
