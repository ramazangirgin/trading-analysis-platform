package tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetAccess;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;

/**
 * Named run configurations (presets): list, create, update and delete. A preset the caller may not see fails
 * with {@code PRESET_NOT_FOUND}, as a missing one does.
 */
public interface ManagePresetsUseCase {

    /**
     * By name: the caller's own presets, or with {@code allUsers} and {@code access.allUsers()} everyone's.
     * {@code allUsers} without the permission is ignored.
     */
    List<Preset> listPresets(PresetAccess access, boolean allUsers);

    Preset createPreset(String name, String payload, OwnerId owner);

    /**
     * Renames a preset and replaces its values; the owner stays.
     *
     * @param expectedVersion the version the caller last saw, or {@code null} to update what is stored;
     *     a different stored version fails with {@code CONCURRENT_UPDATE}
     */
    Preset updatePreset(PresetId id, String name, String payload, Long expectedVersion, PresetAccess access);

    void deletePreset(PresetId id, PresetAccess access);
}
