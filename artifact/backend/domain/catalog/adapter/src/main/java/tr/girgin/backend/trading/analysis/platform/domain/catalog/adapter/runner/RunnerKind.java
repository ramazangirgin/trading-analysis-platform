package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Which runner adapter is active: {@code platform.runner=process} (default: ta-runner in a local
 * venv) or {@code docker} (one container per analysis). PLAN.md section 4, D2/D8.
 */
final class RunnerKind {

    static final String PROPERTY = "platform.runner";

    private RunnerKind() {
    }

    static boolean isDocker(ConditionContext context) {
        return "docker".equalsIgnoreCase(context.getEnvironment().getProperty(PROPERTY, "process").strip());
    }

    static final class Process implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !isDocker(context);
        }
    }

    static final class Docker implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isDocker(context);
        }
    }
}
