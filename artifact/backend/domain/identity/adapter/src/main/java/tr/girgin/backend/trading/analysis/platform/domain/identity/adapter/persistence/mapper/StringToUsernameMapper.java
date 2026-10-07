package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

@Mapper
public interface StringToUsernameMapper {

    default Username map(String source) {
        return new Username(source);
    }
}
