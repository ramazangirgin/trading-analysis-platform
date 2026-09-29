package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.time.Duration;
import org.mapstruct.Mapper;

@Mapper
interface DurationToMillisMapper {

    default long map(Duration source) {
        return source.toMillis();
    }
}
