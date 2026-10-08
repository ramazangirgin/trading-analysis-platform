package tr.girgin.backend.trading.analysis.platform.library.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationToMillisMapperTest {

    private final DurationToMillisMapper mapper = new DurationToMillisMapperImpl();

    @Test
    void mapsADurationToWholeMilliseconds() {
        assertThat(mapper.map(Duration.ofSeconds(260).plusMillis(500))).isEqualTo(260_500L);
    }

    @Test
    void dropsWhatIsFinerThanAMillisecond() {
        assertThat(mapper.map(Duration.ofNanos(1_999_999))).isEqualTo(1L);
    }

    @Test
    void zeroStaysZero() {
        assertThat(mapper.map(Duration.ZERO)).isZero();
    }
}
