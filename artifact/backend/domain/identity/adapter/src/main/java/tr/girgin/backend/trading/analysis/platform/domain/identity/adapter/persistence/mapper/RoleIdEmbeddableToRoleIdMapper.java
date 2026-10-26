package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.RoleIdEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

/** A role ID from its entity key, mapped by its {@code value}. */
@Mapper(config = SharedMapperConfig.class)
public interface RoleIdEmbeddableToRoleIdMapper {

    RoleId map(RoleIdEmbeddable source);
}
