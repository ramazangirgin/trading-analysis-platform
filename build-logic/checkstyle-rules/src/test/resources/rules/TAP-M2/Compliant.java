package tr.girgin.backend.trading.analysis.platform.demo;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface PlainMapper {
    @Mapping(target = "name", source = "value")
    String map(Integer value);
}

@interface Context {}

interface OtherContextUser {
    String map(Integer value, @com.example.other.Context String other);
}
