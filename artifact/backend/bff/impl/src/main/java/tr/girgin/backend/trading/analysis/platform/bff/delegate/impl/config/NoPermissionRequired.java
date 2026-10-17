package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a delegate method that needs no permission: an open route, or one that needs only the session that
 * {@code SecurityConfig} requires. The value is the reason.
 *
 * <p>It changes nothing at runtime ({@link MethodSecurityConfig} matches {@link RequiresPermission} only). It
 * exists so that each exception to the Checkstyle rule {@code TAP-A1} is visible and has a reason.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoPermissionRequired {

    String value();
}
