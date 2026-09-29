package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RunEventDto;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;

@Mapper(uses = RunEventTypeToStringMapper.class)
interface RunEventToRunEventDtoMapper {

    RunEventDto map(RunEvent source);
}
