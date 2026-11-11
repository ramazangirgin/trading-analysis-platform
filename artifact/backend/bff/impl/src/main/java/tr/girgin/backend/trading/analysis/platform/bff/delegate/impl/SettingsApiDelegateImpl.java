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
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManagePresetsPort;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManageSecretsPort;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetAccess;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisdefaults.inbound.ManageAnalysisDefaultsUseCase;

@Service
class SettingsApiDelegateImpl implements SettingsApiDelegate {

    private final ManageSecretsPort manageSecretsPort;
    private final ManagePresetsPort managePresetsPort;
    private final ManageAnalysisDefaultsUseCase manageAnalysisDefaultsUseCase;
    private final SecretStatusToSecretStatusDtoMapper secretStatusToSecretStatusDtoMapper;
    private final PresetToPresetDtoMapper presetToPresetDtoMapper;
    private final AnalysisDefaultsToAnalysisDefaultsDtoMapper analysisDefaultsToAnalysisDefaultsDtoMapper;
    private final PresetValuesDtoToJsonStringMapper presetValuesDtoToJsonStringMapper;
    private final CurrentUser currentUser;
    private final UserDirectoryPort userDirectoryPort;

    @SuppressWarnings("checkstyle:ParameterNumber") // constructor injection of the collaborators above
    SettingsApiDelegateImpl(
            ManageSecretsPort manageSecretsPort,
            ManagePresetsPort managePresetsPort,
            ManageAnalysisDefaultsUseCase manageAnalysisDefaultsUseCase,
            SecretStatusToSecretStatusDtoMapper secretStatusToSecretStatusDtoMapper,
            PresetToPresetDtoMapper presetToPresetDtoMapper,
            AnalysisDefaultsToAnalysisDefaultsDtoMapper analysisDefaultsToAnalysisDefaultsDtoMapper,
            PresetValuesDtoToJsonStringMapper presetValuesDtoToJsonStringMapper,
            CurrentUser currentUser,
            UserDirectoryPort userDirectoryPort) {
        this.currentUser = currentUser;
        this.userDirectoryPort = userDirectoryPort;
        this.manageSecretsPort = manageSecretsPort;
        this.managePresetsPort = managePresetsPort;
        this.manageAnalysisDefaultsUseCase = manageAnalysisDefaultsUseCase;
        this.secretStatusToSecretStatusDtoMapper = secretStatusToSecretStatusDtoMapper;
        this.presetToPresetDtoMapper = presetToPresetDtoMapper;
        this.analysisDefaultsToAnalysisDefaultsDtoMapper = analysisDefaultsToAnalysisDefaultsDtoMapper;
        this.presetValuesDtoToJsonStringMapper = presetValuesDtoToJsonStringMapper;
    }

    @Override
    @RequiresPermission(Permission.SETTINGS_READ)
    public List<SecretStatusDto> listSecrets() {
        return manageSecretsPort.listSecrets().stream()
                .map(secretStatusToSecretStatusDtoMapper::map)
                .toList();
    }

    @Override
    @RequiresPermission(Permission.SETTINGS_KEYS_WRITE)
    public SecretStatusDto setSecret(String name, String value) {
        return secretStatusToSecretStatusDtoMapper.map(manageSecretsPort.setSecret(name, value));
    }

    @Override
    @RequiresPermission(Permission.SETTINGS_KEYS_WRITE)
    public ResponseEntity<Void> removeSecret(String name) {
        manageSecretsPort.removeSecret(name);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(Permission.PRESET_READ)
    public List<PresetDto> listPresets(boolean allUsers) {
        List<Preset> found = managePresetsPort.listPresets(access(), allUsers);
        Map<UserId, Username> usernames = usernames(found);
        return found.stream()
                .map(p -> presetToPresetDtoMapper.map(p, usernames))
                .toList();
    }

    @Override
    @RequiresPermission(Permission.PRESET_MANAGE)
    public ResponseEntity<PresetDto> createPreset(SavePresetRequest request) {
        Preset preset = managePresetsPort.createPreset(
                request.name(), presetValuesDtoToJsonStringMapper.map(request.values()), access().user());
        return ResponseEntity.created(URI.create("/api/presets/" + preset.id().value()))
                .body(dto(preset));
    }

    @Override
    @RequiresPermission(Permission.PRESET_MANAGE)
    public PresetDto updatePreset(String id, SavePresetRequest request) {
        return dto(managePresetsPort.updatePreset(
                new PresetId(id),
                request.name(),
                presetValuesDtoToJsonStringMapper.map(request.values()),
                request.version(),
                access()));
    }

    @Override
    @RequiresPermission(Permission.PRESET_MANAGE)
    public ResponseEntity<Void> deletePreset(String id) {
        managePresetsPort.deletePreset(new PresetId(id), access());
        return ResponseEntity.noContent().build();
    }

    private PresetDto dto(Preset preset) {
        return presetToPresetDtoMapper.map(preset, usernames(List.of(preset)));
    }

    private Map<UserId, Username> usernames(List<Preset> found) {
        Set<UserId> ids = found.stream()
                .map(Preset::owner)
                .filter(Objects::nonNull)
                .map(owner -> new UserId(owner.value()))
                .collect(Collectors.toSet());
        return userDirectoryPort.usernames(ids);
    }

    private PresetAccess access() {
        return new PresetAccess(new OwnerId(currentUser.id().value()), currentUser.has(Permission.PRESET_READ_ALL));
    }

    @Override
    @RequiresPermission({Permission.ANALYSIS_RUN, Permission.ANALYSIS_DEFAULTS_MANAGE})
    public AnalysisDefaultsDto getAnalysisDefaults() {
        return analysisDefaultsToAnalysisDefaultsDtoMapper.map(manageAnalysisDefaultsUseCase.get());
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
        return analysisDefaultsToAnalysisDefaultsDtoMapper.map(
                manageAnalysisDefaultsUseCase.update(defaults, request.version()));
    }
}
