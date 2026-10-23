package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.VisibilityEnumDto;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

@Mapper
public interface VisibilityToVisibilityEnumDtoMapper {

    VisibilityEnumDto map(Visibility source);
}
