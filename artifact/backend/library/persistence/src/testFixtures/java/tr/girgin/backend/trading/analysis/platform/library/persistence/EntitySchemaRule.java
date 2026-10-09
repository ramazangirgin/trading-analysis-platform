package tr.girgin.backend.trading.analysis.platform.library.persistence;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMember;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Every entity of a domain is mapped to the domain's schema: {@code @Table} (and {@code @CollectionTable} /
 * {@code @JoinTable}) name it, and every quoted name in a {@code columnDefinition} or a
 * {@code @ColumnTransformer} write expression is qualified with it. Annotations are read by name, so the
 * rule needs no JPA or Hibernate on its classpath.
 */
final class EntitySchemaRule {

    private static final String ENTITY = "jakarta.persistence.Entity";
    private static final String TABLE = "jakarta.persistence.Table";
    private static final List<String> MEMBER_TABLES =
            List.of("jakarta.persistence.CollectionTable", "jakarta.persistence.JoinTable");
    private static final String COLUMN_TRANSFORMER = "org.hibernate.annotations.ColumnTransformer";
    private static final Pattern QUOTED_NAME = Pattern.compile("\"[^\"]+\"");

    private EntitySchemaRule() {}

    /** The violations in the given classes, one readable line each, sorted. */
    static List<String> violations(JavaClasses classes, String schema) {
        Set<String> violations = new TreeSet<>();
        boolean anyEntity = false;
        for (JavaClass javaClass : classes) {
            if (javaClass.isAnnotatedWith(ENTITY)) {
                anyEntity = true;
                checkTable(violations, javaClass, schema);
            }
            for (JavaMember member : javaClass.getMembers()) {
                checkMember(violations, javaClass, member, schema);
            }
        }
        if (!anyEntity) {
            violations.add("no @Entity found, is the package right?");
        }
        return List.copyOf(violations);
    }

    private static void checkTable(Set<String> violations, JavaClass entity, String schema) {
        Optional<? extends JavaAnnotation<JavaClass>> table = entity.tryGetAnnotationOfType(TABLE);
        if (table.isEmpty()) {
            violations.add("%s: @Entity without @Table".formatted(entity.getSimpleName()));
            return;
        }
        checkSchema(violations, entity.getSimpleName(), table.get(), schema);
    }

    private static void checkMember(Set<String> violations, JavaClass owner, JavaMember member, String schema) {
        String where = owner.getSimpleName() + "." + member.getName();
        for (JavaAnnotation<? extends JavaMember> annotation : member.getAnnotations()) {
            if (MEMBER_TABLES.contains(annotation.getRawType().getName())) {
                checkSchema(violations, where, annotation, schema);
            }
            checkQuotedNames(violations, where, annotation, "columnDefinition", schema);
            if (COLUMN_TRANSFORMER.equals(annotation.getRawType().getName())) {
                checkQuotedNames(violations, where, annotation, "write", schema);
            }
        }
    }

    private static void checkSchema(Set<String> violations, String where, JavaAnnotation<?> annotation, String schema) {
        String name = "@" + annotation.getRawType().getSimpleName();
        Optional<Object> declared = annotation.tryGetExplicitlyDeclaredProperty("schema");
        if (declared.isEmpty() || declared.get().toString().isEmpty()) {
            violations.add("%s: %s has no schema, expected \"%s\"".formatted(where, name, schema));
        } else if (!schema.equals(declared.get())) {
            violations.add("%s: %s has schema \"%s\", expected \"%s\"".formatted(where, name, declared.get(), schema));
        }
    }

    private static void checkQuotedNames(
            Set<String> violations, String where, JavaAnnotation<?> annotation, String property, String schema) {
        Optional<Object> declared = annotation.tryGetExplicitlyDeclaredProperty(property);
        if (declared.isEmpty()) {
            return;
        }
        String text = declared.get().toString();
        Pattern qualifiedName = Pattern.compile("\"" + Pattern.quote(schema) + "\"\\.\"[^\"]+\"");
        if (QUOTED_NAME.matcher(qualifiedName.matcher(text).replaceAll("")).find()) {
            violations.add(
                    "%s: %s [%s] must qualify every quoted name with \"%s\".".formatted(where, property, text, schema));
        }
    }
}
