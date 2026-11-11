package tr.girgin.backend.trading.analysis.platform.library.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnumToLowerCaseNameMapperTest {

    private final EnumToLowerCaseNameMapper enumToLowerCaseNameMapper = new EnumToLowerCaseNameMapperImpl();

    @Test
    void mapsAConstantToItsLowerCaseName() {
        assertThat(enumToLowerCaseNameMapper.map(Sample.STATUS)).isEqualTo("status");
    }

    @Test
    void keepsTheUnderscoresOfAMultiWordConstant() {
        assertThat(enumToLowerCaseNameMapper.map(Sample.SAMPLE_VALUE)).isEqualTo("sample_value");
    }

    private enum Sample {
        STATUS,
        SAMPLE_VALUE
    }
}
