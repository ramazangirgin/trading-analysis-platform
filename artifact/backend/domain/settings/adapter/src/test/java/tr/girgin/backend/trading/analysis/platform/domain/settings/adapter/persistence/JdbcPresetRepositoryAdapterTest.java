package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;

@SpringJUnitConfig(JdbcPresetRepositoryAdapterTest.Config.class)
class JdbcPresetRepositoryAdapterTest {

    @Autowired
    private JdbcPresetRepositoryAdapter repository;

    @Test
    void savesUpdatesAndDeletes() {
        Preset preset = new Preset(PresetId.newId(), "Cheap DeepSeek", "{\"llmProvider\":\"deepseek\"}",
                Instant.parse("2026-09-29T10:00:00Z"));
        repository.save(preset);
        Preset renamed = new Preset(preset.id(), "DeepSeek flash", preset.payload(), Instant.parse("2026-09-29T11:00:00Z"));

        repository.save(renamed);

        assertThat(repository.findById(preset.id())).contains(renamed);
        assertThat(repository.findAll()).containsExactly(renamed);
        assertThat(repository.delete(preset.id())).isTrue();
        assertThat(repository.delete(preset.id())).isFalse();
        assertThat(repository.findAll()).isEmpty();
    }

    @Configuration
    @ComponentScan(basePackageClasses = JdbcPresetRepositoryAdapterTest.class)
    static class Config {

        @Bean
        DataSource dataSource() throws Exception {
            Path db = java.nio.file.Files.createTempFile("presets", ".db");
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite:" + db, true);
            // Only this domain's migration: V1 belongs to another module.
            Flyway.configure().dataSource(dataSource).baselineVersion("1").baselineOnMigrate(true).load().migrate();
            return dataSource;
        }

        @Bean
        JdbcClient jdbcClient(DataSource dataSource) {
            return JdbcClient.create(dataSource);
        }
    }
}
