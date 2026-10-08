package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RunEventDto;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.library.mapper.EnumToLowerCaseNameMapper;

@Mapper(uses = EnumToLowerCaseNameMapper.class)
public interface RunEventToRunEventDtoMapper {

    // The protocol's wire name of an event type is its lower-case constant name.
    @Mapping(target = "type", qualifiedByName = "lowerCaseName")
    RunEventDto map(RunEvent source);
}
