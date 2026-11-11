package tr.girgin.backend.trading.analysis.platform.library.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OptionalInstantToInstantMapperTest {

    private final OptionalInstantToInstantMapper optionalInstantToInstantMapper =
            new OptionalInstantToInstantMapperImpl();

    @Test
    void anEmptyOptionalMapsToNull() {
        assertThat(optionalInstantToInstantMapper.map(Optional.empty())).isNull();
    }

    @Test
    void aPresentOptionalMapsToItsValue() {
        Instant instant = Instant.parse("2026-01-02T03:04:05Z");

        assertThat(optionalInstantToInstantMapper.map(Optional.of(instant))).isEqualTo(instant);
    }
}
