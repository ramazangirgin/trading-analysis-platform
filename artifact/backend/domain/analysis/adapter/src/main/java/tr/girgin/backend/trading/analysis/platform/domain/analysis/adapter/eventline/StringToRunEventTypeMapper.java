package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline;

import java.util.Locale;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;

/** Unknown types become LOG, so a newer runner never breaks an older platform. */
@Mapper
public interface StringToRunEventTypeMapper {

    default RunEventType map(String source) {
        if (source == null) {
            return RunEventType.LOG;
        }
        try {
            return RunEventType.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RunEventType.LOG;
        }
    }
}
