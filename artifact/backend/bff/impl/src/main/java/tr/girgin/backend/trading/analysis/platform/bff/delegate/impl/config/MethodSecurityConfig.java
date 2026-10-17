package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enforces {@link RequiresPermission} on the delegate methods: a call without one of the permissions
 * throws an {@code AccessDeniedException}, which the security chain answers with the 403 {@code forbidden}.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = false)
// HideUtilityClassConstructor: the advisor is a static @Bean method, as Spring requires for an infrastructure bean.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class MethodSecurityConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor requiresPermissionAdvisor() {
        return new AuthorizationManagerBeforeMethodInterceptor(
                new AnnotationMatchingPointcut(null, RequiresPermission.class, true),
                new RequiresPermissionAuthorizationManager());
    }
}
