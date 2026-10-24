package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.SettingsApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDefaultsDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PresetDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SaveAnalysisDefaultsRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SavePresetRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SecretStatusDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.CurrentUser;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.AnalysisDefaultsToAnalysisDefaultsDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.PresetToPresetDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.PresetValuesDtoToJsonStringMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.SecretStatusToSecretStatusDtoMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManagePresetsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManageSecretsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetAccess;
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
    private final PresetValuesDtoToJsonStringMapper valuesMapper;
    private final CurrentUser currentUser;
    private final UserDirectoryUseCase userDirectory;

    @SuppressWarnings("checkstyle:ParameterNumber") // constructor injection of the collaborators above
    SettingsApiDelegateImpl(
            ManageSecretsUseCase secrets,
            ManagePresetsUseCase presets,
            ManageAnalysisDefaultsUseCase analysisDefaults,
            SecretStatusToSecretStatusDtoMapper secretMapper,
            PresetToPresetDtoMapper presetMapper,
            AnalysisDefaultsToAnalysisDefaultsDtoMapper analysisDefaultsMapper,
            PresetValuesDtoToJsonStringMapper valuesMapper,
            CurrentUser currentUser,
            UserDirectoryUseCase userDirectory) {
        this.currentUser = currentUser;
        this.userDirectory = userDirectory;
        this.secrets = secrets;
        this.presets = presets;
        this.analysisDefaults = analysisDefaults;
        this.secretMapper = secretMapper;
        this.presetMapper = presetMapper;
        this.analysisDefaultsMapper = analysisDefaultsMapper;
        this.valuesMapper = valuesMapper;
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
    public List<PresetDto> listPresets(boolean allUsers) {
        List<Preset> found = presets.listPresets(access(), allUsers);
        Map<UserId, Username> usernames = usernames(found);
        return found.stream().map(p -> presetMapper.map(p, usernames)).toList();
    }

    @Override
    @RequiresPermission(Permission.PRESET_MANAGE)
    public ResponseEntity<PresetDto> createPreset(SavePresetRequest request) {
        Preset preset = presets.createPreset(request.name(), valuesMapper.map(request.values()), access().user());
        return ResponseEntity.created(URI.create("/api/presets/" + preset.id().value()))
                .body(dto(preset));
    }

    @Override
    @RequiresPermission(Permission.PRESET_MANAGE)
    public PresetDto updatePreset(String id, SavePresetRequest request) {
        return dto(presets.updatePreset(
                new PresetId(id), request.name(), valuesMapper.map(request.values()), request.version(), access()));
    }

    @Override
    @RequiresPermission(Permission.PRESET_MANAGE)
    public ResponseEntity<Void> deletePreset(String id) {
        presets.deletePreset(new PresetId(id), access());
        return ResponseEntity.noContent().build();
    }

    private PresetDto dto(Preset preset) {
        return presetMapper.map(preset, usernames(List.of(preset)));
    }

    private Map<UserId, Username> usernames(List<Preset> found) {
        Set<UserId> ids = found.stream()
                .map(Preset::owner)
                .filter(Objects::nonNull)
                .map(owner -> new UserId(owner.value()))
                .collect(Collectors.toSet());
        return userDirectory.usernames(ids);
    }

    private PresetAccess access() {
        return new PresetAccess(new OwnerId(currentUser.id().value()), currentUser.has(Permission.PRESET_READ_ALL));
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
