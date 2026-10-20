package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.SettingsApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDefaultsDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PresetDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SaveAnalysisDefaultsRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SavePresetRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SecretStatusDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.AnalysisDefaultsToAnalysisDefaultsDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.MapToJsonStringMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.PresetToPresetDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.SecretStatusToSecretStatusDtoMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManagePresetsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManageSecretsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisdefaults.inbound.ManageAnalysisDefaultsUseCase;

@Service
class SettingsApiDelegateImpl implements SettingsApiDelegate {

    private final ManageSecretsUseCase secrets;
    private final ManagePresetsUseCase presets;
    private final ManageAnalysisDefaultsUseCase analysisDefaults;
    private final SecretStatusToSecretStatusDtoMapper secretMapper;
    private final PresetToPresetDtoMapper presetMapper;
    private final AnalysisDefaultsToAnalysisDefaultsDtoMapper analysisDefaultsMapper;
    private final MapToJsonStringMapper jsonMapper;

    SettingsApiDelegateImpl(
            ManageSecretsUseCase secrets,
            ManagePresetsUseCase presets,
            ManageAnalysisDefaultsUseCase analysisDefaults,
            SecretStatusToSecretStatusDtoMapper secretMapper,
            PresetToPresetDtoMapper presetMapper,
            AnalysisDefaultsToAnalysisDefaultsDtoMapper analysisDefaultsMapper,
            MapToJsonStringMapper jsonMapper) {
        this.secrets = secrets;
        this.presets = presets;
        this.analysisDefaults = analysisDefaults;
        this.secretMapper = secretMapper;
        this.presetMapper = presetMapper;
        this.analysisDefaultsMapper = analysisDefaultsMapper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    @RequiresPermission(Permission.SETTINGS_READ)
    public List<SecretStatusDto> listSecrets() {
        return secrets.listSecrets().stream().map(secretMapper::map).toList();
    }

    @Override
    @RequiresPermission(Permission.SETTINGS_KEYS_WRITE)
    public SecretStatusDto setSecret(String name, String value) {
        return secretMapper.map(secrets.setSecret(name, value));
    }

    @Override
    @RequiresPermission(Permission.SETTINGS_KEYS_WRITE)
    public ResponseEntity<Void> removeSecret(String name) {
        secrets.removeSecret(name);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(Permission.PRESET_READ)
    public List<PresetDto> listPresets() {
        return presets.listPresets().stream().map(presetMapper::map).toList();
    }

    @Override
    @RequiresPermission(Permission.PRESET_MANAGE)
    public ResponseEntity<PresetDto> createPreset(SavePresetRequest request) {
        Preset preset = presets.createPreset(request.name(), jsonMapper.map(request.values()));
        return ResponseEntity.created(URI.create("/api/presets/" + preset.id().value()))
                .body(presetMapper.map(preset));
    }

    @Override
    @RequiresPermission(Permission.PRESET_MANAGE)
    public PresetDto updatePreset(String id, SavePresetRequest request) {
        return presetMapper.map(presets.updatePreset(
                new PresetId(id), request.name(), jsonMapper.map(request.values()), request.version()));
    }

    @Override
    @RequiresPermission(Permission.PRESET_MANAGE)
    public ResponseEntity<Void> deletePreset(String id) {
        presets.deletePreset(new PresetId(id));
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission({Permission.ANALYSIS_RUN, Permission.ANALYSIS_DEFAULTS_MANAGE})
    public AnalysisDefaultsDto getAnalysisDefaults() {
        return analysisDefaultsMapper.map(analysisDefaults.get());
    }

    @Override
    @RequiresPermission(Permission.ANALYSIS_DEFAULTS_MANAGE)
    public AnalysisDefaultsDto saveAnalysisDefaults(SaveAnalysisDefaultsRequest request) {
        AnalysisDefaults defaults = new AnalysisDefaults(
                request.llmProvider(),
                request.deepThinkLlm(),
                request.quickThinkLlm(),
                request.maxDebateRounds(),
                request.maxRiskDiscussRounds(),
                null);
        return analysisDefaultsMapper.map(analysisDefaults.update(defaults, request.version()));
    }
}
