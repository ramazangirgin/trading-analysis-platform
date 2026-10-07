package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class TimestampMappersTest {

    private final InstantToOffsetDateTimeMapper toColumn = Mappers.getMapper(InstantToOffsetDateTimeMapper.class);
    private final OffsetDateTimeToInstantMapper fromColumn = Mappers.getMapper(OffsetDateTimeToInstantMapper.class);
    private final OptionalInstantToOffsetDateTimeMapper optionalToColumn =
            Mappers.getMapper(OptionalInstantToOffsetDateTimeMapper.class);
    private final OffsetDateTimeToOptionalInstantMapper optionalFromColumn =
            Mappers.getMapper(OffsetDateTimeToOptionalInstantMapper.class);

    @Test
    void writesAnInstantInUtc() {
        Instant instant = Instant.parse("2026-10-01T10:00:00.123456Z");

        assertThat(toColumn.map(instant))
                .isEqualTo(OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 123_456_000, ZoneOffset.UTC));
        assertThat(toColumn.map(instant).getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void readsAnyOffsetBackAsTheSameInstant() {
        OffsetDateTime column = OffsetDateTime.of(2026, 10, 1, 13, 0, 0, 0, ZoneOffset.ofHours(3));

        assertThat(fromColumn.map(column)).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
    }

    @Test
    void nullStaysNull() {
        assertThat(toColumn.map(null)).isNull();
        assertThat(fromColumn.map(null)).isNull();
    }

    @Test
    void anOptionalInstantIsAColumnInUtcAndAnEmptyOneIsNull() {
        Instant instant = Instant.parse("2026-10-03T12:00:00Z");

        assertThat(optionalToColumn.map(Optional.of(instant))).isEqualTo(instant.atOffset(ZoneOffset.UTC));
        assertThat(optionalToColumn.map(Optional.empty())).isNull();
    }

    @Test
    void aNullColumnIsAnEmptyOptional() {
        OffsetDateTime column = OffsetDateTime.of(2026, 10, 3, 14, 0, 0, 0, ZoneOffset.ofHours(2));

        assertThat(optionalFromColumn.map(column)).contains(Instant.parse("2026-10-03T12:00:00Z"));
        assertThat(optionalFromColumn.map(null)).isEmpty();
    }
}
