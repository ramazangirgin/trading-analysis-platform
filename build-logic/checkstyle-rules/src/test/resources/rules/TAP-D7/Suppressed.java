package tr.girgin.backend.trading.analysis.platform.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SuppressedConfiguration {
    // Spring needs the interceptor before it creates this configuration.
    @SuppressWarnings("checkstyle:TAP-D7")
    @Bean
    static Object interceptor() {
        return new Object();
    }
}
