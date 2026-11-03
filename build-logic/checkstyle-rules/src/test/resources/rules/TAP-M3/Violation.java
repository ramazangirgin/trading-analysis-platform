package tr.girgin.backend.trading.analysis.platform.demo;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper
interface ExpressionMapper {
    @Mapping(target = "name", expression = "java(value.toString())") // violation
    String map(Integer value);

    @Mapping(target = "name", source = "value", defaultExpression = "java(\"x\")") // violation
    String mapDefault(Integer value);

    @Mapping(target = "name", source = "value", conditionExpression = "java(value != null)") // violation
    String mapCondition(Integer value);

    @Mappings({
        @Mapping(target = "a", expression = "java(1)"), // violation
        @Mapping(target = "b", defaultExpression = "java(2)"), // violation
        @Mapping(target = "c", conditionExpression = "java(true)") // violation
    })
    String mapMany(Integer value);
}
