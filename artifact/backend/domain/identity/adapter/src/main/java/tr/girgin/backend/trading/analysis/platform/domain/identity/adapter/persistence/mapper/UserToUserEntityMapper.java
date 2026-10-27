package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.UserEntity;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(
        config = SharedMapperConfig.class,
        uses = {UserIdToUserIdEmbeddableMapper.class, OptionalInstantToInstantMapper.class})
public interface UserToUserEntityMapper {

    /** The audited {@code createdAt} and {@code updatedAt} are set by auditing, never from the domain. */
    @BeanMapping(ignoreUnmappedSourceProperties = {"createdAt", "updatedAt"})
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserEntity map(User source);
}
