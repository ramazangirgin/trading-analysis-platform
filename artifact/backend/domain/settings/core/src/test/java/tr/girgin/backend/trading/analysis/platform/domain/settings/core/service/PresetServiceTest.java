package tr.girgin.backend.trading.analysis.platform.domain.settings.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsError;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence.PresetRepositoryPort;

class PresetServiceTest {

    private static final Instant STORED_AT = Instant.parse("2026-10-01T10:00:00Z");

    private final FakePresetRepository repository = new FakePresetRepository();
    private final PresetService service = new PresetService(repository);

    @Test
    void createReturnsThePresetAsStoredAndHandsItOverWithoutATime() {
        Preset created = service.createPreset(" Cheap ", "{}");

        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().updatedAt()).isNull();
        assertThat(repository.saved.getFirst().name()).isEqualTo("Cheap");
        assertThat(created.id()).isEqualTo(repository.saved.getFirst().id());
        assertThat(created.updatedAt()).isEqualTo(STORED_AT);
    }

    @Test
    void updateReturnsThePresetAsStoredAndHandsItOverWithoutATime() {
        Preset existing = service.createPreset("Cheap", "{}");
        repository.saved.clear();

        Preset updated = service.updatePreset(existing.id(), "Renamed", "{\"a\":1}", null);

        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().updatedAt()).isNull();
        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.updatedAt()).isEqualTo(STORED_AT);
    }

    @Test
    void updateSavesWithTheLoadedVersionWhenNoneIsGiven() {
        Preset existing = service.createPreset("Cheap", "{}");

        service.updatePreset(existing.id(), "Renamed", "{}", null);

        assertThat(repository.saved.getLast().version()).isEqualTo(existing.version());
    }

    @Test
    void updateSavesWithTheExpectedVersionWhenGiven() {
        Preset existing = service.createPreset("Cheap", "{}");

        assertThatThrownBy(() -> service.updatePreset(existing.id(), "Renamed", "{}", 7L))
                .isInstanceOfSatisfying(
                        SettingsException.class, e -> assertThat(e.error()).isEqualTo(SettingsError.CONCURRENT_UPDATE));
        assertThat(repository.saved.getLast().version()).isEqualTo(7L);
    }

    @Test
    void updateOfAnUnknownPresetIsNotFound() {
        assertThatThrownBy(() -> service.updatePreset(PresetId.newId(), "Renamed", "{}", 0L))
                .isInstanceOfSatisfying(
                        SettingsException.class, e -> assertThat(e.error()).isEqualTo(SettingsError.PRESET_NOT_FOUND));
    }

    /** A port that stamps {@link #STORED_AT}, as auditing does, and rejects a version that is not the stored one. */
    private static final class FakePresetRepository implements PresetRepositoryPort {

        private final List<Preset> saved = new ArrayList<>();
        private final List<Preset> stored = new ArrayList<>();

        @Override
        public List<Preset> findAll() {
            return List.copyOf(stored);
        }

        @Override
        public Optional<Preset> findById(PresetId id) {
            return stored.stream().filter(p -> p.id().equals(id)).findFirst();
        }

        @Override
        public Preset save(Preset preset) {
            saved.add(preset);
            Optional<Preset> current = findById(preset.id());
            if (current.isPresent() && !current.get().version().equals(preset.version())) {
                throw new OptimisticLockingFailureException(
                        "stale " + preset.id().value());
            }
            long next = current.isPresent() ? preset.version() + 1 : 0L;
            Preset result = new Preset(preset.id(), preset.name(), preset.payload(), STORED_AT, next);
            stored.removeIf(p -> p.id().equals(preset.id()));
            stored.add(result);
            return result;
        }

        @Override
        public boolean delete(PresetId id) {
            return stored.removeIf(p -> p.id().equals(id));
        }
    }
}
