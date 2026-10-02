package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;

/** Unknown statuses count as failures. */
@Mapper
public interface StringToRunOutcomeMapper {

    default RunOutcome map(String source) {
        if (source == null) {
            return null;
        }
        return switch (source.trim()) {
            case "completed" -> RunOutcome.COMPLETED;
            case "stopped" -> RunOutcome.STOPPED;
            default -> RunOutcome.FAILED;
        };
    }
}
