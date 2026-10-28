package tr.girgin.backend.trading.analysis.platform.library.mapper;

import java.time.Duration;
import org.mapstruct.Mapper;

@Mapper(config = SharedMapperConfig.class)
public interface DurationToMillisMapper {

    default long map(Duration source) {
        return source.toMillis();
    }
}
