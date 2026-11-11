package tr.girgin.backend.trading.analysis.platform.domain.settings.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsError;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetAccess;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence.PresetRepositoryPort;

class PresetServiceTest {

    private static final Instant STORED_AT = Instant.parse("2026-10-01T10:00:00Z");
    private static final OwnerId ALICE = new OwnerId("u_alice");
    private static final OwnerId BOB = new OwnerId("u_bob");
    private static final PresetAccess ALICE_ACCESS = new PresetAccess(ALICE, false);
    private static final PresetAccess ADMIN_ACCESS = new PresetAccess(new OwnerId("u_admin"), true);

    private final FakePresetRepository fakePresetRepository = new FakePresetRepository();
    private final PresetService presetService = new PresetService(fakePresetRepository);

    @Test
    void createReturnsThePresetAsStoredAndHandsItOverWithoutATime() {
        Preset created = presetService.createPreset(" Cheap ", "{}", ALICE);

        assertThat(fakePresetRepository.saved).hasSize(1);
        assertThat(fakePresetRepository.saved.getFirst().updatedAt()).isNull();
        assertThat(fakePresetRepository.saved.getFirst().name()).isEqualTo("Cheap");
        assertThat(created.id()).isEqualTo(fakePresetRepository.saved.getFirst().id());
        assertThat(created.updatedAt()).isEqualTo(STORED_AT);
    }

    @Test
    void aPresetBelongsToItsCreator() {
        Preset created = presetService.createPreset("Cheap", "{}", ALICE);

        assertThat(created.owner()).isEqualTo(ALICE);
    }

    @Test
    void updateReturnsThePresetAsStoredAndHandsItOverWithoutATime() {
        Preset existing = presetService.createPreset("Cheap", "{}", ALICE);
        fakePresetRepository.saved.clear();

        Preset updated = presetService.updatePreset(existing.id(), "Renamed", "{\"a\":1}", null, ALICE_ACCESS);

        assertThat(fakePresetRepository.saved).hasSize(1);
        assertThat(fakePresetRepository.saved.getFirst().updatedAt()).isNull();
        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.updatedAt()).isEqualTo(STORED_AT);
    }

    @Test
    void updateSavesWithTheLoadedVersionWhenNoneIsGiven() {
        Preset existing = presetService.createPreset("Cheap", "{}", ALICE);

        presetService.updatePreset(existing.id(), "Renamed", "{}", null, ALICE_ACCESS);

        assertThat(fakePresetRepository.saved.getLast().version()).isEqualTo(existing.version());
    }

    @Test
    void updateSavesWithTheExpectedVersionWhenGiven() {
        Preset existing = presetService.createPreset("Cheap", "{}", ALICE);

        assertThatThrownBy(() -> presetService.updatePreset(existing.id(), "Renamed", "{}", 7L, ALICE_ACCESS))
                .isInstanceOfSatisfying(
                        SettingsException.class, e -> assertThat(e.error()).isEqualTo(SettingsError.CONCURRENT_UPDATE));
        assertThat(fakePresetRepository.saved.getLast().version()).isEqualTo(7L);
    }

    @Test
    void updateOfAnUnknownPresetIsNotFound() {
        assertThatThrownBy(() -> presetService.updatePreset(
                        new PresetId(UUID.randomUUID().toString()), "Renamed", "{}", 0L, ALICE_ACCESS))
                .isInstanceOfSatisfying(
                        SettingsException.class, e -> assertThat(e.error()).isEqualTo(SettingsError.PRESET_NOT_FOUND));
    }

    @Test
    void anUpdateKeepsTheOwnerAlsoWhenAnAdminMakesIt() {
        Preset existing = presetService.createPreset("Cheap", "{}", ALICE);

        Preset updated = presetService.updatePreset(existing.id(), "Renamed", "{}", null, ADMIN_ACCESS);

        assertThat(updated.owner()).isEqualTo(ALICE);
    }

    @Test
    void listReturnsOnlyTheCallersOwnPresetsByName() {
        presetService.createPreset("b", "{}", ALICE);
        presetService.createPreset("A", "{}", ALICE);
        presetService.createPreset("Other", "{}", BOB);

        assertThat(presetService.listPresets(ALICE_ACCESS, false))
                .extracting(Preset::name)
                .containsExactly("A", "b");
    }

    @Test
    void listWithAllUsersWithoutThePermissionIsIgnored() {
        presetService.createPreset("Mine", "{}", ALICE);
        presetService.createPreset("Other", "{}", BOB);

        assertThat(presetService.listPresets(ALICE_ACCESS, true))
                .extracting(Preset::name)
                .containsExactly("Mine");
    }

    @Test
    void listWithAllUsersAndThePermissionReturnsEveryonesPresets() {
        presetService.createPreset("Mine", "{}", ALICE);
        presetService.createPreset("Other", "{}", BOB);

        assertThat(presetService.listPresets(ADMIN_ACCESS, true))
                .extracting(Preset::name)
                .containsExactly("Mine", "Other");
    }

    @Test
    void listWithThePermissionButWithoutAllUsersReturnsTheCallersOwnPresets() {
        presetService.createPreset("Other", "{}", BOB);

        assertThat(presetService.listPresets(ADMIN_ACCESS, false)).isEmpty();
    }

    @Test
    void updateOfAnotherUsersPresetIsNotFoundAndLeavesItUnchanged() {
        Preset others = presetService.createPreset("Other", "{}", BOB);
        fakePresetRepository.saved.clear();

        assertThatThrownBy(() -> presetService.updatePreset(others.id(), "Renamed", "{}", null, ALICE_ACCESS))
                .isInstanceOfSatisfying(
                        SettingsException.class, e -> assertThat(e.error()).isEqualTo(SettingsError.PRESET_NOT_FOUND));
        assertThat(fakePresetRepository.saved).isEmpty();
    }

    @Test
    void updateOfAPresetWithoutOwnerIsNotFoundForAnOrdinaryUser() {
        Preset unowned = fakePresetRepository.seed(null);

        assertThatThrownBy(() -> presetService.updatePreset(unowned.id(), "Renamed", "{}", null, ALICE_ACCESS))
                .isInstanceOfSatisfying(
                        SettingsException.class, e -> assertThat(e.error()).isEqualTo(SettingsError.PRESET_NOT_FOUND));
    }

    @Test
    void deleteOfAnotherUsersPresetIsNotFoundAndKeepsIt() {
        Preset others = presetService.createPreset("Other", "{}", BOB);

        assertThatThrownBy(() -> presetService.deletePreset(others.id(), ALICE_ACCESS))
                .isInstanceOfSatisfying(
                        SettingsException.class, e -> assertThat(e.error()).isEqualTo(SettingsError.PRESET_NOT_FOUND));
        assertThat(fakePresetRepository.findById(others.id())).isPresent();
    }

    @Test
    void anAdminDeletesAnotherUsersPreset() {
        Preset others = presetService.createPreset("Other", "{}", BOB);

        presetService.deletePreset(others.id(), ADMIN_ACCESS);

        assertThat(fakePresetRepository.findById(others.id())).isEmpty();
    }

    @Test
    void deleteOfAnUnknownPresetIsNotFound() {
        assertThatThrownBy(() -> presetService.deletePreset(
                        new PresetId(UUID.randomUUID().toString()), ADMIN_ACCESS))
                .isInstanceOfSatisfying(
                        SettingsException.class, e -> assertThat(e.error()).isEqualTo(SettingsError.PRESET_NOT_FOUND));
    }

    @Test
    void assignUnownedTouchesOnlyPresetsWithoutAnOwner() {
        Preset owned = presetService.createPreset("Owned", "{}", BOB);
        Preset unowned = fakePresetRepository.seed(null);

        int assigned = presetService.assignUnowned(ALICE);

        assertThat(assigned).isEqualTo(1);
        assertThat(fakePresetRepository.findById(unowned.id()).orElseThrow().owner())
                .isEqualTo(ALICE);
        assertThat(fakePresetRepository.findById(owned.id()).orElseThrow().owner())
                .isEqualTo(BOB);
    }

    /** A port that stamps {@link #STORED_AT}, as auditing does, and rejects a version that is not the stored one. */
    private static final class FakePresetRepository implements PresetRepositoryPort {

        private final List<Preset> saved = new ArrayList<>();
        private final List<Preset> stored = new ArrayList<>();

        Preset seed(OwnerId owner) {
            Preset preset =
                    new Preset(new PresetId(UUID.randomUUID().toString()), "Seeded", "{}", owner, STORED_AT, 0L);
            stored.add(preset);
            return preset;
        }

        @Override
        public List<Preset> findAll(OwnerId owner) {
            return stored.stream()
                    .filter(p -> owner == null || owner.equals(p.owner()))
                    .toList();
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
            Preset result = new Preset(preset.id(), preset.name(), preset.payload(), preset.owner(), STORED_AT, next);
            stored.removeIf(p -> p.id().equals(preset.id()));
            stored.add(result);
            return result;
        }

        @Override
        public boolean delete(PresetId id) {
            return stored.removeIf(p -> p.id().equals(id));
        }

        @Override
        public int assignOwnerWhereMissing(OwnerId owner) {
            List<Preset> unowned =
                    stored.stream().filter(p -> p.owner() == null).toList();
            unowned.forEach(p -> save(new Preset(p.id(), p.name(), p.payload(), owner, null, p.version())));
            return unowned.size();
        }
    }
}
