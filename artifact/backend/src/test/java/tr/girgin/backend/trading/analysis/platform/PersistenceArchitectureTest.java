package tr.girgin.backend.trading.analysis.platform;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import javax.sql.DataSource;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

// ConstantName: ArchUnit reports a rule by its field name, so rule names are snake_case sentences.
// DeclarationOrder: the package names come first, then the rules that use them, grouped by topic.
// HideUtilityClassConstructor: JUnit instantiates the test class.
/**
 * The persistence and shared-library rules (docs/coding-convention/backend-java-persistence.md,
 * backend-java-package-structure.md "Shared libraries"); the other structure rules are in
 * {@link ArchitectureTest}.
 */
@SuppressWarnings({"checkstyle:ConstantName", "checkstyle:DeclarationOrder", "checkstyle:HideUtilityClassConstructor"})
@AnalyzeClasses(
        packages = "tr.girgin.backend.trading.analysis.platform",
        importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class PersistenceArchitectureTest {

    private static final String BASE = "tr.girgin.backend.trading.analysis.platform";
    private static final String ROOT = "tr.girgin.backend.trading.analysis.platform..";
    private static final String DOMAIN_ADAPTER_PERSISTENCE = BASE + ".domain.*.adapter.persistence";
    private static final String DOMAIN_ADAPTER_ENTITY = BASE + ".domain.*.adapter.*.entity..";
    private static final String LIBRARY = BASE + ".library..";
    private static final String PERSISTENCE_CONFIGURATION = "PersistenceConfiguration";

    /** JPA mapping classes: entities, embeddables, mapped superclasses and attribute converters. */
    // One explicit predicate: a chain of DescribedPredicate.or(...) over these matched no class at all.
    private static final DescribedPredicate<JavaClass> JPA_MAPPING = DescribedPredicate.describe(
            "JPA mapping classes",
            javaClass -> javaClass.isAnnotatedWith(Entity.class)
                    || javaClass.isAnnotatedWith(Embeddable.class)
                    || javaClass.isAnnotatedWith(MappedSuperclass.class)
                    || javaClass.isAnnotatedWith(Converter.class)
                    || javaClass.isAssignableTo(AttributeConverter.class));

    /** Methods of {@code EntityManager} and Hibernate's {@code Session} that run plain SQL. */
    private static final Set<String> NATIVE_QUERY_METHODS = Set.of(
            "createNativeQuery",
            "createNativeMutationQuery",
            "createStoredProcedureQuery",
            "createNamedStoredProcedureQuery",
            "createStoredProcedureCall");

    /** The Spring Data auditing annotations, which only work with the auditing entity listener. */
    private static final Set<Class<? extends Annotation>> AUDITING_ANNOTATIONS =
            Set.of(CreatedDate.class, LastModifiedDate.class, CreatedBy.class, LastModifiedBy.class);

    // --- Persistence -----------------------------------------------------------------------

    @ArchTest
    static final ArchRule production_code_does_not_use_plain_sql = noClasses()
            .that()
            .resideInAPackage(ROOT)
            .and()
            .haveSimpleNameNotEndingWith(PERSISTENCE_CONFIGURATION)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.jdbc..", "java.sql..", "javax.sql..")
            .because("persistence goes through Spring Data JPA (docs/coding-convention/backend-java-persistence.md)");

    @ArchTest
    static final ArchRule persistence_configurations_only_hand_the_data_source_to_flyway = noClasses()
            .that()
            .resideInAPackage(ROOT)
            .and()
            .haveSimpleNameEndingWith(PERSISTENCE_CONFIGURATION)
            .should()
            .dependOnClassesThat(resideInAnyPackage("org.springframework.jdbc..", "java.sql..", "javax.sql..")
                    .and(not(equivalentTo(DataSource.class))))
            .because("a domain's persistence configuration takes the DataSource only to hand it to its Flyway bean;"
                    + " all other plain SQL stays banned (docs/coding-convention/backend-java-persistence.md)");

    @ArchTest
    static final ArchRule no_native_queries = classes()
            .that()
            .resideInAPackage(ROOT)
            .should(notUseNativeQueries())
            .because("a native query is plain SQL: queries are derived, JPQL or Specifications");

    @ArchTest
    static final ArchRule entities_live_in_entity_packages = classes()
            .that(JPA_MAPPING)
            .should()
            .resideInAPackage(DOMAIN_ADAPTER_ENTITY)
            .because("entities, embeddables and attribute converters are table shapes of one adapter port");

    @ArchTest
    static final ArchRule entity_packages_hold_only_entities = topLevelClasses()
            .and()
            .resideInAPackage(DOMAIN_ADAPTER_ENTITY)
            .should()
            .beAnnotatedWith(Entity.class)
            .orShould()
            .beAnnotatedWith(Embeddable.class)
            .orShould()
            .beAnnotatedWith(MappedSuperclass.class)
            .orShould()
            .beAssignableTo(AttributeConverter.class);

    @ArchTest
    static final ArchRule spring_data_repositories_live_in_persistence_roots = classes()
            .that()
            .resideInAPackage(ROOT)
            .and()
            .areAssignableTo(Repository.class)
            .should()
            .resideInAPackage(DOMAIN_ADAPTER_PERSISTENCE)
            .because("a Spring Data repository is used by its persistence adapter only, next to it");

    @ArchTest
    static final ArchRule persistence_classes_are_named_by_kind = CompositeArchRule.of(
                    classes().that().areAnnotatedWith(Entity.class).should().haveSimpleNameEndingWith("Entity"))
            .and(topLevelClasses()
                    .and()
                    .resideInAPackage(ROOT)
                    .and()
                    .haveSimpleNameEndingWith("Entity")
                    .should()
                    .beAnnotatedWith(Entity.class))
            .and(classes().that().areAnnotatedWith(Embeddable.class).should().haveSimpleNameEndingWith("Embeddable"))
            .and(classes()
                    .that()
                    .resideInAPackage(ROOT)
                    .and()
                    .areAssignableTo(AttributeConverter.class)
                    .should()
                    .haveSimpleNameEndingWith("AttributeConverter"))
            .and(classes()
                    .that()
                    .resideInAPackage(ROOT)
                    .and()
                    .areAssignableTo(Repository.class)
                    .should()
                    .haveSimpleNameEndingWith("JpaRepository"))
            .because("the suffix says what a class is: AnalysisEntity is the table, Analysis the domain");

    @ArchTest
    static final ArchRule enums_are_not_stored_as_text = classes()
            .that(DescribedPredicate.describe(
                    "entities and embeddables",
                    javaClass ->
                            javaClass.isAnnotatedWith(Entity.class) || javaClass.isAnnotatedWith(Embeddable.class)))
            .should(mapEnumsToPostgresEnumTypes())
            .because("an enum column is a PostgreSQL enum type and a set of enums an array of it, never text");

    @ArchTest
    static final ArchRule audited_entities_have_the_auditing_listener = classes()
            .that(DescribedPredicate.describe(
                    "have an auditing field",
                    javaClass -> javaClass.getFields().stream()
                            .anyMatch(field -> AUDITING_ANNOTATIONS.stream().anyMatch(field::isAnnotatedWith))))
            .should(haveTheAuditingListener())
            .because("without the listener the auditing annotations are silently ignored, and a NOT NULL column"
                    + " fails only on insert");

    // --- Shared libraries --------------------------------------------------------------------

    @ArchTest
    static final ArchRule libraries_depend_only_on_libraries = noClasses()
            .that()
            .resideInAPackage(LIBRARY)
            .should()
            .dependOnClassesThat(resideInAPackage(ROOT).and(not(resideInAPackage(LIBRARY))))
            .because("a shared library holds technical code only: it cannot even name a domain, BFF or"
                    + " orchestration type");

    /** Top-level project classes, without {@code package-info} and generated nested types. */
    private static GivenClassesConjunction topLevelClasses() {
        return classes().that().areTopLevelClasses().and().doNotHaveSimpleName("package-info");
    }

    private static ArchCondition<JavaClass> notUseNativeQueries() {
        return new ArchCondition<>("not use native queries") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethod method : javaClass.getMethods()) {
                    boolean nativeQuery = method.isAnnotatedWith(NativeQuery.class)
                            || method.tryGetAnnotationOfType(Query.class)
                                    .map(Query::nativeQuery)
                                    .orElse(false);
                    if (nativeQuery) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName() + " is a native query"));
                    }
                }
                javaClass.getMethodCallsFromSelf().stream()
                        .filter(call -> NATIVE_QUERY_METHODS.contains(call.getName()))
                        .filter(call -> call.getTargetOwner().getPackageName().startsWith("jakarta.persistence")
                                || call.getTargetOwner().getPackageName().startsWith("org.hibernate"))
                        .forEach(call -> events.add(SimpleConditionEvent.violated(call, call.getDescription())));
            }
        };
    }

    private static ArchCondition<JavaClass> mapEnumsToPostgresEnumTypes() {
        return new ArchCondition<>("map every enum field to a PostgreSQL enum type or an array of one") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaField field : javaClass.getFields()) {
                    if (!holdsEnums(field.getType())) {
                        continue;
                    }
                    int jdbcType = field.tryGetAnnotationOfType(JdbcTypeCode.class)
                            .map(JdbcTypeCode::value)
                            .orElse(Integer.MIN_VALUE);
                    if (jdbcType != SqlTypes.NAMED_ENUM && jdbcType != SqlTypes.ARRAY) {
                        events.add(SimpleConditionEvent.violated(
                                field,
                                field.getFullName() + " holds an enum without @JdbcTypeCode(NAMED_ENUM) or"
                                        + " @JdbcTypeCode(ARRAY)"));
                    } else if (jdbcType == SqlTypes.ARRAY
                            && field.tryGetAnnotationOfType(ColumnTransformer.class)
                                    .map(ColumnTransformer::write)
                                    .filter(write -> !write.isBlank())
                                    .isEmpty()) {
                        events.add(SimpleConditionEvent.violated(
                                field,
                                field.getFullName() + " is an enum array without a @ColumnTransformer(write ="
                                        + " \"cast(? as ...[])\"): Hibernate binds varchar[]"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> haveTheAuditingListener() {
        return new ArchCondition<>("be annotated with @EntityListeners(AuditingEntityListener.class)") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean listed = javaClass
                        .tryGetAnnotationOfType(EntityListeners.class)
                        .map(listeners -> Arrays.asList(listeners.value()).contains(AuditingEntityListener.class))
                        .orElse(false);
                if (!listed) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            javaClass.getName() + " has an auditing field but no"
                                    + " @EntityListeners(AuditingEntityListener.class)"));
                }
            }
        };
    }

    /** An enum, or a collection or array of one. */
    private static boolean holdsEnums(JavaType type) {
        if (type.toErasure().isEnum()) {
            return true;
        }
        if (type instanceof JavaParameterizedType parameterized
                && parameterized.toErasure().isAssignableTo(Collection.class)) {
            return parameterized.getActualTypeArguments().stream()
                    .anyMatch(argument -> argument.toErasure().isEnum());
        }
        return type.toErasure().isArray() && type.toErasure().getComponentType().isEnum();
    }
}
