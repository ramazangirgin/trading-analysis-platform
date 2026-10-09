package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Turns on JPA auditing ({@code @CreatedDate}, {@code @LastModifiedDate}), timed by the {@link Clock} bean. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "clockDateTimeProvider")
public class JpaAuditingConfiguration {

    @Bean
    ClockDateTimeProvider clockDateTimeProvider(Clock clock) {
        return new ClockDateTimeProvider(clock);
    }
}
