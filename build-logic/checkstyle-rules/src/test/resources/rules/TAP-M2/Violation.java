package tr.girgin.backend.trading.analysis.platform.demo;

import java.util.Set;
import org.mapstruct.Context;
import org.mapstruct.Mapper;

@Mapper
interface ContextMapper {
    String map(Integer value, @Context Set<String> names); // violation

    String mapQualified(Integer value, @org.mapstruct.Context Set<String> names); // violation
}
