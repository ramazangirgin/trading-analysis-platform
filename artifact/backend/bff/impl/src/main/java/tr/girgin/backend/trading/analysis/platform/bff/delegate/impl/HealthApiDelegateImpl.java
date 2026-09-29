package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.HealthApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SystemHealthDto;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.SystemHealthUseCase;

@Service
class HealthApiDelegateImpl implements HealthApiDelegate {

    private final SystemHealthUseCase systemHealth;
    private final SystemHealthToSystemHealthDtoMapper mapper;

    HealthApiDelegateImpl(SystemHealthUseCase systemHealth, SystemHealthToSystemHealthDtoMapper mapper) {
        this.systemHealth = systemHealth;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<SystemHealthDto> getHealth() {
        return ResponseEntity.ok(mapper.map(systemHealth.checkHealth()));
    }
}
