package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.SettingsApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PresetDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SavePresetRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SecretStatusDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.MapToJsonStringMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.PresetToPresetDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.SecretStatusToSecretStatusDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.SettingsExceptionToApiExceptionMapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManagePresetsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManageSecretsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;

@Service
class SettingsApiDelegateImpl implements SettingsApiDelegate {

    private final ManageSecretsUseCase secrets;
    private final ManagePresetsUseCase presets;
    private final SecretStatusToSecretStatusDtoMapper secretMapper;
    private final PresetToPresetDtoMapper presetMapper;
    private final MapToJsonStringMapper jsonMapper;
    private final SettingsExceptionToApiExceptionMapper errorMapper;

    SettingsApiDelegateImpl(
            ManageSecretsUseCase secrets,
            ManagePresetsUseCase presets,
            SecretStatusToSecretStatusDtoMapper secretMapper,
            PresetToPresetDtoMapper presetMapper,
            MapToJsonStringMapper jsonMapper,
            SettingsExceptionToApiExceptionMapper errorMapper) {
        this.secrets = secrets;
        this.presets = presets;
        this.secretMapper = secretMapper;
        this.presetMapper = presetMapper;
        this.jsonMapper = jsonMapper;
        this.errorMapper = errorMapper;
    }

    @Override
    public ResponseEntity<List<SecretStatusDto>> listSecrets() {
        return ResponseEntity.ok(
                secrets.listSecrets().stream().map(secretMapper::map).toList());
    }

    @Override
    public ResponseEntity<SecretStatusDto> setSecret(String name, String value) {
        return ResponseEntity.ok(secretMapper.map(call(() -> secrets.setSecret(name, value))));
    }

    @Override
    public ResponseEntity<Void> removeSecret(String name) {
        call(() -> {
            secrets.removeSecret(name);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<PresetDto>> listPresets() {
        return ResponseEntity.ok(
                presets.listPresets().stream().map(presetMapper::map).toList());
    }

    @Override
    public ResponseEntity<PresetDto> createPreset(SavePresetRequest request) {
        Preset preset = call(() -> presets.createPreset(request.name(), jsonMapper.map(request.values())));
        return ResponseEntity.created(URI.create("/api/presets/" + preset.id().value()))
                .body(presetMapper.map(preset));
    }

    @Override
    public ResponseEntity<PresetDto> updatePreset(String id, SavePresetRequest request) {
        return ResponseEntity.ok(presetMapper.map(
                call(() -> presets.updatePreset(new PresetId(id), request.name(), jsonMapper.map(request.values())))));
    }

    @Override
    public ResponseEntity<Void> deletePreset(String id) {
        call(() -> {
            presets.deletePreset(new PresetId(id));
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (SettingsException e) {
            throw errorMapper.map(e);
        }
    }
}
