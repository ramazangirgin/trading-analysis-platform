package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.Locale;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.DebateSpeaker;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(config = SharedMapperConfig.class)
public interface DebateSpeakerToStringMapper {

    default String map(DebateSpeaker source) {
        return source.name().toLowerCase(Locale.ROOT);
    }
}
