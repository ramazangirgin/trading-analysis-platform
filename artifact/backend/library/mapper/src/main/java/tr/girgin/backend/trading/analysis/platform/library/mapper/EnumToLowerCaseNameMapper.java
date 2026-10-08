package tr.girgin.backend.trading.analysis.platform.library.mapper;

import java.util.Locale;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

/**
 * The lower-case constant name of an enum, e.g. {@code AGENT_STATUS} as {@code agent_status}. Only
 * applied where a mapping asks for it with {@code qualifiedByName = "lowerCaseName"}, so no other
 * enum to String mapping changes.
 */
@Mapper
public interface EnumToLowerCaseNameMapper {

    @Named("lowerCaseName")
    default <E extends Enum<E>> String map(E source) {
        return source.name().toLowerCase(Locale.ROOT);
    }
}
