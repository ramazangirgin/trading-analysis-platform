package tr.girgin.backend.trading.analysis.platform.domain.settings.core.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsError;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManagePresetsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence.PresetRepositoryPort;

@Service
class PresetService implements ManagePresetsUseCase {

    private static final int MAX_NAME = 80;
    private static final int MAX_PAYLOAD = 20_000;

    private final PresetRepositoryPort repository;

    PresetService(PresetRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<Preset> listPresets() {
        return repository.findAll().stream().sorted(Comparator.comparing(p -> p.name().toLowerCase())).toList();
    }

    @Override
    public Preset createPreset(String name, String payload) {
        Preset preset = new Preset(PresetId.newId(), validName(name), validPayload(payload), Instant.now());
        repository.save(preset);
        return preset;
    }

    @Override
    public Preset updatePreset(PresetId id, String name, String payload) {
        repository.findById(id).orElseThrow(() -> notFound(id));
        Preset preset = new Preset(id, validName(name), validPayload(payload), Instant.now());
        repository.save(preset);
        return preset;
    }

    @Override
    public void deletePreset(PresetId id) {
        if (!repository.delete(id)) {
            throw notFound(id);
        }
    }

    private static String validName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME) {
            throw new SettingsException(SettingsError.INVALID_PRESET, "Invalid preset name", Map.of("field", "name"));
        }
        return trimmed;
    }

    private static String validPayload(String payload) {
        if (payload == null || payload.isBlank() || payload.length() > MAX_PAYLOAD) {
            throw new SettingsException(SettingsError.INVALID_PRESET, "Invalid preset payload",
                    Map.of("field", "payload"));
        }
        return payload;
    }

    private static SettingsException notFound(PresetId id) {
        return new SettingsException(SettingsError.PRESET_NOT_FOUND, "Preset not found: " + id.value(),
                Map.of("id", id.value()));
    }
}
