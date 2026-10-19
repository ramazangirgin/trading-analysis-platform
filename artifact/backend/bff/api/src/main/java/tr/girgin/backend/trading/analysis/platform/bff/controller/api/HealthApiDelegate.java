package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SystemHealthDto;

public interface HealthApiDelegate {

    SystemHealthDto getHealth();
}
