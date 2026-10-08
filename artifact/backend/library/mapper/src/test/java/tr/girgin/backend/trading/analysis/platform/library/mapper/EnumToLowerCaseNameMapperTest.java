package tr.girgin.backend.trading.analysis.platform.library.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnumToLowerCaseNameMapperTest {

    private final EnumToLowerCaseNameMapper mapper = new EnumToLowerCaseNameMapperImpl();

    @Test
    void mapsAConstantToItsLowerCaseName() {
        assertThat(mapper.map(Sample.STATUS)).isEqualTo("status");
    }

    @Test
    void keepsTheUnderscoresOfAMultiWordConstant() {
        assertThat(mapper.map(Sample.AGENT_STATUS)).isEqualTo("agent_status");
    }

    private enum Sample {
        STATUS,
        AGENT_STATUS
    }
}
