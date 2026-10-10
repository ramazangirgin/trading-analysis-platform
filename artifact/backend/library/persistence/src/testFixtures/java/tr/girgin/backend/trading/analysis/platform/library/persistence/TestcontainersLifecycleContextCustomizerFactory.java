package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.util.List;
import org.springframework.boot.testcontainers.lifecycle.TestcontainersLifecycleApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Applies Spring Boot's {@link TestcontainersLifecycleApplicationContextInitializer} to the context of a
 * {@code @JpaAdapterTest} class, which is not started by {@code SpringApplication}. Registered in
 * {@code META-INF/spring.factories}. The {@code @SpringBootTest} classes do not need it, so it leaves them alone.
 */
public class TestcontainersLifecycleContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        if (!AnnotatedElementUtils.hasAnnotation(testClass, JpaAdapterTest.class)) {
            return null;
        }
        return new TestcontainersLifecycleContextCustomizer();
    }

    /** Equal to every other instance, so the context cache is not split by it. */
    private record TestcontainersLifecycleContextCustomizer() implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            new TestcontainersLifecycleApplicationContextInitializer().initialize(context);
        }
    }
}
