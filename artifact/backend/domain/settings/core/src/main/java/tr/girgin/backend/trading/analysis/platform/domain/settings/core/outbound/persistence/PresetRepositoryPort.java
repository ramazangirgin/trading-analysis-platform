package tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence;

import java.util.List;
import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;

/** Stores presets. */
public interface PresetRepositoryPort {

    List<Preset> findAll();

    Optional<Preset> findById(PresetId id);

    /**
     * Inserts or updates the preset and returns it as stored, with its audited {@code updatedAt} and its new
     * {@link Preset#version()}. A preset whose version differs from the stored one fails with Spring's
     * {@code OptimisticLockingFailureException} and leaves the row unchanged; a {@code null} version means
     * "insert", so it fails on an existing ID as well.
     */
    Preset save(Preset preset);

    boolean delete(PresetId id);
}
