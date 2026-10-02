package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.time.Duration;
import org.mapstruct.Mapper;

@Mapper
public interface DurationToMillisMapper {

    default long map(Duration source) {
        return source.toMillis();
    }
}
