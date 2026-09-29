package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SystemHealthDto;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.HealthCheck;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.SystemHealth;

@Mapper
interface SystemHealthToSystemHealthDtoMapper {

    @Mapping(target = "overall", expression = "java(source.overall().name())")
    SystemHealthDto map(SystemHealth source);

    SystemHealthDto.HealthCheckDto map(HealthCheck source);
}
