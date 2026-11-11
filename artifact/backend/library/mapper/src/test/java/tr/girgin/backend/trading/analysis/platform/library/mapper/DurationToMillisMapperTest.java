package tr.girgin.backend.trading.analysis.platform.library.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationToMillisMapperTest {

    private final DurationToMillisMapper durationToMillisMapper = new DurationToMillisMapperImpl();

    @Test
    void mapsADurationToWholeMilliseconds() {
        assertThat(durationToMillisMapper.map(Duration.ofSeconds(260).plusMillis(500)))
                .isEqualTo(260_500L);
    }

    @Test
    void dropsWhatIsFinerThanAMillisecond() {
        assertThat(durationToMillisMapper.map(Duration.ofNanos(1_999_999))).isEqualTo(1L);
    }

    @Test
    void zeroStaysZero() {
        assertThat(durationToMillisMapper.map(Duration.ZERO)).isZero();
    }
}
