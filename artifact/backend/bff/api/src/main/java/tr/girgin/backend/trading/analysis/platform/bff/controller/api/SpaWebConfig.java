package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the bundled Vue build and falls back to {@code index.html} for client-side routes.
 *
 * <p>Controllers and Actuator are matched before this handler, so only unmatched paths get here.
 * Unknown {@code /api} and {@code /actuator} paths and missing assets (anything with a file
 * extension) stay 404 instead of returning the SPA shell.
 */
@Configuration
class SpaWebConfig implements WebMvcConfigurer {

    private static final String INDEX = "index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    private static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (!resourcePath.isEmpty()) {
                Resource requested = location.createRelative(resourcePath);
                if (requested.exists() && requested.isReadable()) {
                    return requested;
                }
            }
            if (isClientRoute(resourcePath)) {
                Resource index = location.createRelative(INDEX);
                return index.exists() ? index : null;
            }
            return null;
        }

        private static boolean isClientRoute(String path) {
            return !path.startsWith("api/")
                    && !path.equals("api")
                    && !path.startsWith("actuator/")
                    && !path.substring(path.lastIndexOf('/') + 1).contains(".");
        }
    }
}
