package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import java.time.Duration;
import org.mapstruct.Mapper;

@Mapper
public interface MillisToDurationMapper {

    default Duration map(long source) {
        return Duration.ofMillis(source);
    }
}
