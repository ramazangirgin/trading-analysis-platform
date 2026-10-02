package tr.girgin.backend.trading.analysis.platform.orchestration.health.inbound;

import tr.girgin.backend.trading.analysis.platform.orchestration.health.model.SystemHealth;

public interface SystemHealthUseCase {

    SystemHealth checkHealth();
}
