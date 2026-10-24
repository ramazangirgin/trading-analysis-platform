package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDefaultsDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PresetDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SaveAnalysisDefaultsRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SavePresetRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SecretStatusDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SetSecretRequest;

@RestController
@RequestMapping("/api")
public class SettingsApiController {

    private final SettingsApiDelegate delegate;

    public SettingsApiController(SettingsApiDelegate delegate) {
        this.delegate = delegate;
    }

    @GetMapping("/secrets")
    public List<SecretStatusDto> listSecrets() {
        return delegate.listSecrets();
    }

    @PutMapping("/secrets/{name}")
    public SecretStatusDto setSecret(@PathVariable String name, @Valid @RequestBody SetSecretRequest request) {
        return delegate.setSecret(name, request.value());
    }

    @DeleteMapping("/secrets/{name}")
    public ResponseEntity<Void> removeSecret(@PathVariable String name) {
        return delegate.removeSecret(name);
    }

    @GetMapping("/presets")
    public List<PresetDto> listPresets(@RequestParam(defaultValue = "false") boolean allUsers) {
        return delegate.listPresets(allUsers);
    }

    @PostMapping("/presets")
    public ResponseEntity<PresetDto> createPreset(@Valid @RequestBody SavePresetRequest request) {
        return delegate.createPreset(request);
    }

    @PutMapping("/presets/{id}")
    public PresetDto updatePreset(@PathVariable String id, @Valid @RequestBody SavePresetRequest request) {
        return delegate.updatePreset(id, request);
    }

    @DeleteMapping("/presets/{id}")
    public ResponseEntity<Void> deletePreset(@PathVariable String id) {
        return delegate.deletePreset(id);
    }

    @GetMapping("/settings/analysis-defaults")
    public AnalysisDefaultsDto getAnalysisDefaults() {
        return delegate.getAnalysisDefaults();
    }

    @PutMapping("/settings/analysis-defaults")
    public AnalysisDefaultsDto saveAnalysisDefaults(@Valid @RequestBody SaveAnalysisDefaultsRequest request) {
        return delegate.saveAnalysisDefaults(request);
    }
}
