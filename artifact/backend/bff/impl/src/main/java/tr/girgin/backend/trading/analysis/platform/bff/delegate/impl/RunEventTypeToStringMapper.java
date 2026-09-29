package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.util.Locale;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;

/** The protocol's wire name ("agent_status"), which the frontend switches on. */
@Mapper
interface RunEventTypeToStringMapper {

    default String map(RunEventType source) {
        return source.name().toLowerCase(Locale.ROOT);
    }
}
