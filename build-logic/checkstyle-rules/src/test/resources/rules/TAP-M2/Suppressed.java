package tr.girgin.backend.trading.analysis.platform.demo;

import java.util.Set;
import org.mapstruct.Context;
import org.mapstruct.Mapper;

// A generated-code test needs a context parameter.
@SuppressWarnings("checkstyle:TAP-M2")
@Mapper
interface SuppressedMapper {
    String map(Integer value, @Context Set<String> names);
}
