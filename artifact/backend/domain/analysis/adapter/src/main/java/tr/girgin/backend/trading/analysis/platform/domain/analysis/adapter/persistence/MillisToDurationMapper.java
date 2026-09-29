package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import java.time.Duration;
import org.mapstruct.Mapper;

@Mapper
interface MillisToDurationMapper {

    default Duration map(long source) {
        return Duration.ofMillis(source);
    }
}
