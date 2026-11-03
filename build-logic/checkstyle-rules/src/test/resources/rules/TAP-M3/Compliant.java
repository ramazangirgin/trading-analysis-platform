package tr.girgin.backend.trading.analysis.platform.demo;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper
interface PlainMapper {
    @Mapping(target = "name", source = "value")
    String map(Integer value);

    @Mapping(target = "name", constant = "none")
    String mapConstant(Integer value);

    @Mapping(target = "name", source = "value", defaultValue = "none")
    String mapDefault(Integer value);

    @Mappings({
        @Mapping(target = "a", source = "value", qualifiedByName = "upper"),
        @Mapping(target = "b", constant = "b")
    })
    String mapMany(Integer value);
}

class Holder {
    String compute(Integer value) {
        String expression = "java(1)";
        return expression + value;
    }
}
