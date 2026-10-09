package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
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
import tr.girgin.backend.trading.analysis.platform.library.persistence.MutableTestClock;
import tr.girgin.backend.trading.analysis.platform.library.persistence.PostgresTestDatabase;

@JpaAdapterTest
@SpringJUnitConfig(JpaPresetRepositoryAdapterTest.Config.class)
class JpaPresetRepositoryAdapterTest {

    @Autowired
    private JpaPresetRepositoryAdapter repository;

    @Autowired
    private MutableTestClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(MutableTestClock.START);
    }

    @Test
    void savesUpdatesAndDeletes() {
        Preset preset = new Preset(PresetId.newId(), "Cheap DeepSeek", "{\"llmProvider\":\"deepseek\"}", null);
        Preset saved = repository.save(preset);
        clock.advance(Duration.ofHours(1));
        Preset renamed = repository.save(new Preset(preset.id(), "DeepSeek flash", preset.payload(), null));

        assertThat(saved).isEqualTo(new Preset(preset.id(), preset.name(), preset.payload(), MutableTestClock.START));
        Instant later = MutableTestClock.START.plus(Duration.ofHours(1));
        assertThat(renamed).isEqualTo(new Preset(preset.id(), "DeepSeek flash", preset.payload(), later));
        assertThat(repository.findById(preset.id())).contains(renamed);
        assertThat(repository.findAll()).containsExactly(renamed);
        assertThat(repository.delete(preset.id())).isTrue();
        assertThat(repository.delete(preset.id())).isFalse();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void aNewPresetGetsTheTimeOfTheClockInTheReturnedRecordAndOnRead() {
        Preset saved = repository.save(new Preset(PresetId.newId(), "A", "{}", null));

        assertThat(saved.updatedAt()).isEqualTo(MutableTestClock.START);
        assertThat(repository.findById(saved.id()).orElseThrow().updatedAt()).isEqualTo(MutableTestClock.START);
    }

    @Test
    void anUpdateWithUnchangedNameAndPayloadStillMovesUpdatedAt() {
        Preset saved = repository.save(new Preset(PresetId.newId(), "A", "{}", null));
        clock.advance(Duration.ofMinutes(5));

        Preset again = repository.save(new Preset(saved.id(), saved.name(), saved.payload(), null));

        Instant expected = MutableTestClock.START.plus(Duration.ofMinutes(5));
        assertThat(again.updatedAt()).isEqualTo(expected);
        assertThat(repository.findById(saved.id()).orElseThrow().updatedAt()).isEqualTo(expected);
    }

    @Configuration
    @AutoConfigurationPackage
    @ComponentScan(basePackageClasses = JpaPresetRepositoryAdapterTest.class)
    static class Config {

        @Bean
        DataSource dataSource() {
            return PostgresTestDatabase.create().dataSource();
        }
    }
}
