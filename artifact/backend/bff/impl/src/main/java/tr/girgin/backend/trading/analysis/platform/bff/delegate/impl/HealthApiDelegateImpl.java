package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.HealthApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SystemHealthDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.SystemHealthToSystemHealthDtoMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.inbound.SystemHealthUseCase;

@Service
class HealthApiDelegateImpl implements HealthApiDelegate {

    private final SystemHealthUseCase systemHealthUseCase;
    private final SystemHealthToSystemHealthDtoMapper systemHealthToSystemHealthDtoMapper;

    HealthApiDelegateImpl(
            SystemHealthUseCase systemHealthUseCase,
            SystemHealthToSystemHealthDtoMapper systemHealthToSystemHealthDtoMapper) {
        this.systemHealthUseCase = systemHealthUseCase;
        this.systemHealthToSystemHealthDtoMapper = systemHealthToSystemHealthDtoMapper;
    }

    @Override
    // Its alerts are about running (runner, Docker, missing keys).
    @RequiresPermission({Permission.ANALYSIS_RUN, Permission.SETTINGS_READ})
    public SystemHealthDto getHealth() {
        return systemHealthToSystemHealthDtoMapper.map(systemHealthUseCase.checkHealth());
    }
}
