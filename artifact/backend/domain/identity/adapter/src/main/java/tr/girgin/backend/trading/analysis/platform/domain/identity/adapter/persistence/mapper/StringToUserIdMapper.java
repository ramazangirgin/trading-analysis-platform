package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

@Mapper
public interface StringToUserIdMapper {

    default UserId map(String source) {
        return new UserId(source);
    }
}
