package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline;

import java.math.BigDecimal;
import java.time.Duration;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;

/** Stats exist only on STATS lines; other lines map to null. */
@Mapper
public interface RunnerOutputLineToRunStatsMapper {

    default RunStats map(RunnerOutputLine source) {
        if (source == null || source.llmCalls() == null) {
            return null;
        }
        return new RunStats(
                source.llmCalls(),
                source.toolCalls() == null ? 0 : source.toolCalls(),
                source.tokensIn() == null ? 0 : source.tokensIn(),
                source.tokensOut() == null ? 0 : source.tokensOut(),
                source.costUsd() == null ? null : BigDecimal.valueOf(source.costUsd()),
                source.elapsedS() == null ? Duration.ZERO : Duration.ofMillis(Math.round(source.elapsedS() * 1000)));
    }
}
