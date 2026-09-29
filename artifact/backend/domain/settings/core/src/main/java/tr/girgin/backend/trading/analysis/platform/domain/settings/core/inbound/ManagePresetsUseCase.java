package tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;

public interface ManagePresetsUseCase {

    /** By name. */
    List<Preset> listPresets();

    Preset createPreset(String name, String payload);

    Preset updatePreset(PresetId id, String name, String payload);

    void deletePreset(PresetId id);
}
