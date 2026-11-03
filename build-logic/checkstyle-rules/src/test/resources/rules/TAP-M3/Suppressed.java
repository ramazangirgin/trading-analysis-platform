package tr.girgin.backend.trading.analysis.platform.demo;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// A generated-code test needs an expression.
@SuppressWarnings("checkstyle:TAP-M3")
@Mapper
interface SuppressedMapper {
    @Mapping(target = "name", expression = "java(value.toString())")
    String map(Integer value);
}
