package tr.girgin.backend.trading.analysis.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Fully qualified bean names: every module has MapStruct mappers, and generated beans with the
 * same simple name in different packages (e.g. two DurationToMillisMapper) would otherwise clash.
 */
@SpringBootApplication(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor") // Spring instantiates the @SpringBootApplication class
public class TradingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingPlatformApplication.class, args);
    }
}
