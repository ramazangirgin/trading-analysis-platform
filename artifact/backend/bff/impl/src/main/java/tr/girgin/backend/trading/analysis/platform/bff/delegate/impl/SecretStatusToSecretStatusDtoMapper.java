package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SecretStatusDto;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.SecretStatus;

@Mapper
interface SecretStatusToSecretStatusDtoMapper {

    SecretStatusDto map(SecretStatus source);
}
