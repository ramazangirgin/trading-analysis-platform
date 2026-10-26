package tr.girgin.backend.trading.analysis.platform.demo;

import org.mapstruct.Mapper;

@Mapper // violation
interface PlainMapper {
    String map(Integer value);
}

@Mapper(uses = PlainMapper.class) // violation
interface ConverterMapper {
    String map(Integer value);
}
