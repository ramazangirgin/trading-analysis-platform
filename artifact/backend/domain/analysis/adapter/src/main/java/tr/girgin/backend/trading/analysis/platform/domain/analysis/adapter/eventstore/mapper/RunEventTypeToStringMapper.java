package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventstore.mapper;

import java.util.Locale;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;

/** The protocol's wire name of an event type. */
@Mapper
public interface RunEventTypeToStringMapper {

    default String map(RunEventType source) {
        return source.name().toLowerCase(Locale.ROOT);
    }
}
