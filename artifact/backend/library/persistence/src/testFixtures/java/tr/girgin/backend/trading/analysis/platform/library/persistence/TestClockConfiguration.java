package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.time.Clock;
import org.springframework.context.annotation.Bean;

/**
 * The {@link Clock} bean of an adapter test: a {@link MutableTestClock}, injectable as such to move the time.
 * Not annotated {@code @Configuration}, only imported by {@code @JpaAdapterTest}: the application's component
 * scan covers this package, and the application tests have the test fixtures on their classpath, so a scanned
 * configuration would clash with the application's {@code Clock}.
 */
public class TestClockConfiguration {

    @Bean
    MutableTestClock clock() {
        return new MutableTestClock();
    }
}
