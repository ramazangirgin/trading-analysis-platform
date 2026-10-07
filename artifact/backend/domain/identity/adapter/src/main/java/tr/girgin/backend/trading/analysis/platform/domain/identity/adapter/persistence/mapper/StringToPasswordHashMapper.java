package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;

@Mapper
public interface StringToPasswordHashMapper {

    default PasswordHash map(String source) {
        return new PasswordHash(source);
    }
}
