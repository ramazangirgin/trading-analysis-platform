package tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;

/** Named run configurations (presets): list, create, update and delete. */
public interface ManagePresetsUseCase {

    /** By name. */
    List<Preset> listPresets();

    Preset createPreset(String name, String payload);

    /**
     * Renames a preset and replaces its values.
     *
     * @param expectedVersion the version the caller last saw, or {@code null} to update what is stored;
     *     a different stored version fails with {@code CONCURRENT_UPDATE}
     */
    Preset updatePreset(PresetId id, String name, String payload, Long expectedVersion);

    void deletePreset(PresetId id);
}
