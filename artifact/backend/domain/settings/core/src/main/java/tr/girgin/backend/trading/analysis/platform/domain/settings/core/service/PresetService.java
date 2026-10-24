package tr.girgin.backend.trading.analysis.platform.domain.settings.core.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsError;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.AssignUnownedPresetsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManagePresetsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetAccess;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence.PresetRepositoryPort;

@Service
class PresetService implements ManagePresetsUseCase, AssignUnownedPresetsUseCase {

    private static final int MAX_NAME = 80;
    private static final int MAX_PAYLOAD = 20_000;

    private final PresetRepositoryPort repository;

    PresetService(PresetRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<Preset> listPresets(PresetAccess access, boolean allUsers) {
        OwnerId owner = allUsers && access.allUsers() ? null : access.user();
        return repository.findAll(owner).stream()
                .sorted(Comparator.comparing(p -> p.name().toLowerCase()))
                .toList();
    }

    @Override
    public Preset createPreset(String name, String payload, OwnerId owner) {
        return repository.save(new Preset(PresetId.newId(), validName(name), validPayload(payload), owner, null, null));
    }

    @Override
    public Preset updatePreset(PresetId id, String name, String payload, Long expectedVersion, PresetAccess access) {
        Preset stored = visible(id, access);
        Long version = expectedVersion == null ? stored.version() : expectedVersion;
        try {
            return repository.save(
                    new Preset(id, validName(name), validPayload(payload), stored.owner(), null, version));
        } catch (OptimisticLockingFailureException _) {
            throw new SettingsException(
                    SettingsError.CONCURRENT_UPDATE,
                    "Preset was changed concurrently: " + id.value(),
                    Map.of("id", id.value()));
        }
    }

    @Override
    public void deletePreset(PresetId id, PresetAccess access) {
        visible(id, access);
        if (!repository.delete(id)) {
            throw notFound(id);
        }
    }

    @Override
    public int assignUnowned(OwnerId owner) {
        return repository.assignOwnerWhereMissing(owner);
    }

    private Preset visible(PresetId id, PresetAccess access) {
        return repository.findById(id).filter(access::canSee).orElseThrow(() -> notFound(id));
    }

    private String validName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME) {
            throw new SettingsException(SettingsError.INVALID_PRESET, "Invalid preset name", Map.of("field", "name"));
        }
        return trimmed;
    }

    private String validPayload(String payload) {
        if (payload == null || payload.isBlank() || payload.length() > MAX_PAYLOAD) {
            throw new SettingsException(
                    SettingsError.INVALID_PRESET, "Invalid preset payload", Map.of("field", "payload"));
        }
        return payload;
    }

    private SettingsException notFound(PresetId id) {
        return new SettingsException(
                SettingsError.PRESET_NOT_FOUND, "Preset not found: " + id.value(), Map.of("id", id.value()));
    }
}
