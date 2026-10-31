package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SystemHealthDto;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.model.HealthCheck;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.model.SystemHealth;

@Mapper(config = SharedMapperConfig.class)
public interface SystemHealthToSystemHealthDtoMapper {

    SystemHealthDto map(SystemHealth source);

    SystemHealthDto.HealthCheckDto map(HealthCheck source);
}
