package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;

/**
 * The permission an endpoint needs, declared on its delegate method: the caller needs <b>any</b> of the
 * given permissions, or the call is refused with an {@code AccessDeniedException} (a 403 {@code forbidden}).
 * {@link MethodSecurityConfig} enforces it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    Permission[] value();
}
