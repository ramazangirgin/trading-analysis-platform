package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.library.persistence.JpaAdapterTest;
import tr.girgin.backend.trading.analysis.platform.library.persistence.MutableTestClock;

@JpaAdapterTest
@SpringJUnitConfig(JpaPresetRepositoryAdapterTest.Config.class)
class JpaPresetRepositoryAdapterTest {

    private static final OwnerId ALICE = new OwnerId("u_alice");
    private static final OwnerId BOB = new OwnerId("u_bob");

    @Autowired
    private JpaPresetRepositoryAdapter jpaPresetRepositoryAdapter;

    @Autowired
    private MutableTestClock clock;

    @Autowired
    private PresetJpaRepository presetJpaRepository;

    private static PresetId newId() {
        return new PresetId(UUID.randomUUID().toString());
    }

    @BeforeEach
    void emptyTableAndResetClock() {
        presetJpaRepository.deleteAll();
        clock.set(MutableTestClock.START);
    }

    @Test
    void savesUpdatesAndDeletes() {
        Preset preset = new Preset(newId(), "Cheap DeepSeek", "{\"llmProvider\":\"deepseek\"}", ALICE, null, null);
        Preset saved = jpaPresetRepositoryAdapter.save(preset);
        clock.advance(Duration.ofHours(1));
        Preset renamed = jpaPresetRepositoryAdapter.save(
                new Preset(preset.id(), "DeepSeek flash", preset.payload(), ALICE, null, 0L));

        assertThat(saved)
                .isEqualTo(new Preset(preset.id(), preset.name(), preset.payload(), ALICE, MutableTestClock.START, 0L));
        Instant later = MutableTestClock.START.plus(Duration.ofHours(1));
        assertThat(renamed).isEqualTo(new Preset(preset.id(), "DeepSeek flash", preset.payload(), ALICE, later, 1L));
        assertThat(jpaPresetRepositoryAdapter.findById(preset.id())).contains(renamed);
        assertThat(jpaPresetRepositoryAdapter.findAll(null)).containsExactly(renamed);
        assertThat(jpaPresetRepositoryAdapter.delete(preset.id())).isTrue();
        assertThat(jpaPresetRepositoryAdapter.delete(preset.id())).isFalse();
        assertThat(jpaPresetRepositoryAdapter.findAll(null)).isEmpty();
    }

    @Test
    void theOwnerIsStoredAndReadBack() {
        Preset saved = jpaPresetRepositoryAdapter.save(new Preset(newId(), "A", "{}", ALICE, null, null));

        assertThat(saved.owner()).isEqualTo(ALICE);
        assertThat(jpaPresetRepositoryAdapter.findById(saved.id()).orElseThrow().owner())
                .isEqualTo(ALICE);
    }

    @Test
    void findAllFiltersByOwnerAndNullMeansEveryOwner() {
        Preset alices = jpaPresetRepositoryAdapter.save(new Preset(newId(), "A", "{}", ALICE, null, null));
        Preset bobs = jpaPresetRepositoryAdapter.save(new Preset(newId(), "B", "{}", BOB, null, null));
        Preset unowned = jpaPresetRepositoryAdapter.save(new Preset(newId(), "C", "{}", null, null, null));

        assertThat(jpaPresetRepositoryAdapter.findAll(ALICE)).containsExactly(alices);
        assertThat(jpaPresetRepositoryAdapter.findAll(BOB)).containsExactly(bobs);
        assertThat(jpaPresetRepositoryAdapter.findAll(null)).containsExactlyInAnyOrder(alices, bobs, unowned);
    }

    @Test
    void savingAnUpdateWithTheStoredOwnerKeepsIt() {
        Preset saved = jpaPresetRepositoryAdapter.save(new Preset(newId(), "A", "{}", ALICE, null, null));

        Preset again = jpaPresetRepositoryAdapter.save(
                new Preset(saved.id(), "B", "{}", saved.owner(), null, saved.version()));

        assertThat(again.owner()).isEqualTo(ALICE);
        assertThat(jpaPresetRepositoryAdapter.findById(saved.id()).orElseThrow().owner())
                .isEqualTo(ALICE);
    }

    @Test
    void assignOwnerWhereMissingSetsOnlyMissingOwnersAndRaisesTheirVersion() {
        Preset owned = jpaPresetRepositoryAdapter.save(new Preset(newId(), "A", "{}", BOB, null, null));
        Preset unowned = jpaPresetRepositoryAdapter.save(new Preset(newId(), "B", "{}", null, null, null));

        int assigned = jpaPresetRepositoryAdapter.assignOwnerWhereMissing(ALICE);

        assertThat(assigned).isEqualTo(1);
        assertThat(jpaPresetRepositoryAdapter.findById(owned.id())).contains(owned);
        Preset now = jpaPresetRepositoryAdapter.findById(unowned.id()).orElseThrow();
        assertThat(now.owner()).isEqualTo(ALICE);
        assertThat(now.version()).isEqualTo(unowned.version() + 1);
        assertThat(jpaPresetRepositoryAdapter.assignOwnerWhereMissing(ALICE)).isZero();
    }

    @Test
    void aNewPresetGetsTheTimeOfTheClockInTheReturnedRecordAndOnRead() {
        Preset saved = jpaPresetRepositoryAdapter.save(new Preset(newId(), "A", "{}", ALICE, null, null));

        assertThat(saved.updatedAt()).isEqualTo(MutableTestClock.START);
        assertThat(jpaPresetRepositoryAdapter.findById(saved.id()).orElseThrow().updatedAt())
                .isEqualTo(MutableTestClock.START);
    }

    @Test
    void anUpdateWithUnchangedNameAndPayloadStillMovesUpdatedAt() {
        Preset saved = jpaPresetRepositoryAdapter.save(new Preset(newId(), "A", "{}", ALICE, null, null));
        clock.advance(Duration.ofMinutes(5));

        Preset again = jpaPresetRepositoryAdapter.save(
                new Preset(saved.id(), saved.name(), saved.payload(), ALICE, null, saved.version()));

        Instant expected = MutableTestClock.START.plus(Duration.ofMinutes(5));
        assertThat(again.updatedAt()).isEqualTo(expected);
        assertThat(jpaPresetRepositoryAdapter.findById(saved.id()).orElseThrow().updatedAt())
                .isEqualTo(expected);
    }

    @Test
    void aStaleSaveFailsAndLeavesTheNewerRowUnchanged() {
        Preset saved = jpaPresetRepositoryAdapter.save(new Preset(newId(), "A", "{}", ALICE, null, null));
        Preset firstCopy = jpaPresetRepositoryAdapter.findById(saved.id()).orElseThrow();
        Preset staleCopy = jpaPresetRepositoryAdapter.findById(saved.id()).orElseThrow();
        Preset firstWrite = jpaPresetRepositoryAdapter.save(
                new Preset(saved.id(), "First", "{}", ALICE, null, firstCopy.version()));

        assertThatThrownBy(() -> jpaPresetRepositoryAdapter.save(
                        new Preset(saved.id(), "Second", "{}", ALICE, null, staleCopy.version())))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(firstWrite.version()).isEqualTo(1);
        assertThat(jpaPresetRepositoryAdapter.findById(saved.id())).contains(firstWrite);
    }

    @Test
    void savingWithoutAVersionFailsOnAnExistingId() {
        Preset saved = jpaPresetRepositoryAdapter.save(new Preset(newId(), "A", "{}", ALICE, null, null));

        assertThatThrownBy(() -> jpaPresetRepositoryAdapter.save(new Preset(saved.id(), "B", "{}", ALICE, null, null)))
                .isInstanceOf(DataAccessException.class);

        assertThat(jpaPresetRepositoryAdapter.findById(saved.id())).contains(saved);
    }

    @Test
    void savingAVersionForADeletedRowFails() {
        Preset saved = jpaPresetRepositoryAdapter.save(new Preset(newId(), "A", "{}", ALICE, null, null));
        jpaPresetRepositoryAdapter.delete(saved.id());

        assertThatThrownBy(() -> jpaPresetRepositoryAdapter.save(saved))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Configuration
    @AutoConfigurationPackage
    @ComponentScan(basePackageClasses = JpaPresetRepositoryAdapterTest.class)
    static class Config {}
}
