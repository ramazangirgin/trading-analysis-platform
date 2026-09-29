package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PresetDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SavePresetRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SecretStatusDto;

public interface SettingsApiDelegate {

    ResponseEntity<List<SecretStatusDto>> listSecrets();

    ResponseEntity<SecretStatusDto> setSecret(String name, String value);

    ResponseEntity<Void> removeSecret(String name);

    ResponseEntity<List<PresetDto>> listPresets();

    ResponseEntity<PresetDto> createPreset(SavePresetRequest request);

    ResponseEntity<PresetDto> updatePreset(String id, SavePresetRequest request);

    ResponseEntity<Void> deletePreset(String id);
}
