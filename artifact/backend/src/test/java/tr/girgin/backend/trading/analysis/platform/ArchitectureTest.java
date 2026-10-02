package tr.girgin.backend.trading.analysis.platform;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// ConstantName: ArchUnit reports a rule by its field name, so rule names are snake_case sentences.
// DeclarationOrder: the package names come first, then the rules that use them, grouped by topic.
// HideUtilityClassConstructor: JUnit instantiates the test class.
@SuppressWarnings({"checkstyle:ConstantName", "checkstyle:DeclarationOrder", "checkstyle:HideUtilityClassConstructor"})
@AnalyzeClasses(packages = "tr.girgin.backend.trading.analysis.platform",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "tr.girgin.backend.trading.analysis.platform";
    private static final String ROOT = "tr.girgin.backend.trading.analysis.platform..";
    private static final String BFF = "tr.girgin.backend.trading.analysis.platform.bff..";
    private static final String BFF_API = "tr.girgin.backend.trading.analysis.platform.bff.controller.api..";
    private static final String BFF_IMPL = "tr.girgin.backend.trading.analysis.platform.bff.delegate.impl..";
    private static final String DOMAIN = "tr.girgin.backend.trading.analysis.platform.domain..";
    private static final String DOMAIN_CORE = "tr.girgin.backend.trading.analysis.platform.domain.*.core..";
    private static final String DOMAIN_MODEL = "tr.girgin.backend.trading.analysis.platform.domain.*.core.model..";
    private static final String DOMAIN_SERVICE = "tr.girgin.backend.trading.analysis.platform.domain.*.core.service..";
    private static final String DOMAIN_OUTBOUND =
            "tr.girgin.backend.trading.analysis.platform.domain.*.core.outbound..";
    private static final String DOMAIN_ADAPTER = "tr.girgin.backend.trading.analysis.platform.domain.*.adapter..";
    private static final String DOMAIN_RUNNER_ADAPTER =
            "tr.girgin.backend.trading.analysis.platform.domain.*.adapter.runner..";
    private static final String ORCHESTRATION = "tr.girgin.backend.trading.analysis.platform.orchestration..";

    // Package placement (doc/coding-convention/backend-java-package-structure.md).
    private static final String BFF_API_ROOT = BASE + ".bff.controller.api";
    private static final String BFF_IMPL_ROOT = BASE + ".bff.delegate.impl";
    private static final String BFF_IMPL_MAPPER = BASE + ".bff.delegate.impl.mapper..";
    private static final String DOMAIN_ADAPTER_PORT = BASE + ".domain.*.adapter.*";
    private static final String DOMAIN_ADAPTER_PORT_SUB = BASE + ".domain.*.adapter.*.*..";
    private static final String DOMAIN_ADAPTER_MAPPER = BASE + ".domain.*.adapter..mapper..";
    private static final String DOMAIN_CORE_SUB = BASE + ".domain.*.core.*..";
    private static final String ORCHESTRATION_FEATURE_SUB = BASE + ".orchestration.*.*..";
    private static final String INBOUND = "..inbound..";
    private static final String SERVICE = "..service..";
    private static final String CORE_EXCEPTION = BASE + ".domain.*.core.exception..";
    private static final String BFF_API_ERROR = BASE + ".bff.controller.api.error..";
    private static final String ORCHESTRATION_SERVICE = BASE + ".orchestration.*.service..";

    /** Shared helper packages under {@code adapter} that serve several ports and implement none. */
    private static final String[] SHARED_ADAPTER_PACKAGES = {BASE + ".domain.analysis.adapter.eventline"};

    private static final DescribedPredicate<JavaClass> MAPSTRUCT_MAPPER = annotatedWith(Mapper.class).forSubtype();

    // --- BFF -------------------------------------------------------------------------------

    @ArchTest
    static final ArchRule bff_api_does_not_use_domain_models = noClasses()
            .that().resideInAPackage(BFF_API)
            .should().dependOnClassesThat().resideInAPackage(DOMAIN_MODEL)
            .because("the API exposes web DTOs only");

    @ArchTest
    static final ArchRule bff_api_only_depends_on_itself = noClasses()
            .that().resideInAPackage(BFF_API)
            .should().dependOnClassesThat(resideInAPackage(ROOT).and(not(resideInAPackage(BFF_API))));

    @ArchTest
    static final ArchRule bff_api_is_only_used_by_bff_controllers = classes()
            .that().resideInAPackage(BFF_API)
            .should().onlyHaveDependentClassesThat().resideInAnyPackage(BFF_API, BFF_IMPL);

    @ArchTest
    static final ArchRule bff_impl_is_only_used_by_itself = classes()
            .that().resideInAPackage(BFF_IMPL)
            .should().onlyHaveDependentClassesThat().resideInAPackage(BFF_IMPL);

    @ArchTest
    static final ArchRule bff_does_not_use_outbound_ports = noClasses()
            .that().resideInAPackage(BFF)
            .should().dependOnClassesThat().resideInAPackage(DOMAIN_OUTBOUND)
            .because("the BFF reaches domains through inbound ports only");

    @ArchTest
    static final ArchRule domains_and_orchestration_do_not_depend_on_bff = noClasses()
            .that().resideInAnyPackage(DOMAIN, ORCHESTRATION)
            .should().dependOnClassesThat().resideInAPackage(BFF);

    // --- Domains ---------------------------------------------------------------------------

    @ArchTest
    static final ArchRule domain_services_are_only_used_by_themselves = classes()
            .that().resideInAPackage(DOMAIN_SERVICE)
            .should().onlyHaveDependentClassesThat().resideInAPackage(DOMAIN_SERVICE)
            .because("services are reached through inbound ports only");

    @ArchTest
    static final ArchRule orchestration_services_are_only_used_by_themselves = classes()
            .that().resideInAPackage(ORCHESTRATION_SERVICE)
            .should().onlyHaveDependentClassesThat().resideInAPackage(ORCHESTRATION_SERVICE)
            .because("orchestrators, like domain services, are reached through inbound ports only");

    @ArchTest
    static final ArchRule adapters_are_only_used_by_themselves = classes()
            .that().resideInAPackage(DOMAIN_ADAPTER)
            .should().onlyHaveDependentClassesThat().resideInAPackage(DOMAIN_ADAPTER);

    @ArchTest
    static final ArchRule adapters_only_implement_outbound_ports_or_mappers = classes()
            .that().resideInAPackage(DOMAIN_ADAPTER)
            .should(implementOnlyProjectInterfacesThat(resideInAPackage(DOMAIN_OUTBOUND).or(MAPSTRUCT_MAPPER)));

    @ArchTest
    static final ArchRule adapter_components_implement_an_outbound_port = classes()
            .that().resideInAPackage(DOMAIN_ADAPTER)
            .and().areMetaAnnotatedWith(Component.class)
            .and(not(assignableTo(MAPSTRUCT_MAPPER)))
            .should().implement(resideInAPackage(DOMAIN_OUTBOUND));

    @ArchTest
    static final ArchRule domain_core_does_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage(DOMAIN_CORE)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.github.dockerjava..",
                    "java.sql..",
                    "org.springframework.jdbc..",
                    "org.springframework.data..",
                    "com.fasterxml.jackson..",
                    "tools.jackson..")
            .orShould().dependOnClassesThat().belongToAnyOf(ProcessBuilder.class, Process.class)
            .because("infrastructure is an outbound adapter concern");

    @ArchTest
    static final ArchRule only_runner_adapters_execute_commands = noClasses()
            .that().resideOutsideOfPackage(DOMAIN_RUNNER_ADAPTER)
            .should().dependOnClassesThat().belongToAnyOf(ProcessBuilder.class, Process.class)
            .orShould().dependOnClassesThat().resideInAPackage("com.github.dockerjava..")
            .because("adapter.runner is the single place that starts processes or talks to Docker");

    @ArchTest
    static final ArchRule domains_are_independent = slices()
            .matching("tr.girgin.backend.trading.analysis.platform.domain.(*)..")
            .should().notDependOnEachOther()
            .because("logic spanning several domains belongs in the orchestration package");

    @ArchTest
    static final ArchRule domains_do_not_depend_on_orchestration = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAPackage(ORCHESTRATION);

    // --- Package placement -----------------------------------------------------------------
    // Every kind of class has one place; see doc/coding-convention/backend-java-package-structure.md.

    @ArchTest
    static final ArchRule module_root_packages_hold_no_classes = topLevelClasses()
            .should().resideOutsideOfPackages(
                    BASE + ".bff", BASE + ".bff.controller", BASE + ".bff.delegate",
                    BASE + ".domain", BASE + ".domain.*", BASE + ".domain.*.core", BASE + ".domain.*.adapter",
                    BASE + ".orchestration", BASE + ".orchestration.*")
            .because("a module's root package only groups its sub-packages (package-info aside)");

    @ArchTest
    static final ArchRule mapper_packages_hold_only_mappers = topLevelClasses()
            .and().resideInAPackage("..mapper..")
            .should().beAssignableTo(MAPSTRUCT_MAPPER);

    @ArchTest
    static final ArchRule exception_mappers_live_in_mapper_error = classes()
            .that().areAnnotatedWith(Mapper.class)
            .and().haveSimpleNameEndingWith("ExceptionToApiExceptionMapper")
            .should().resideInAPackage(BFF_IMPL_ROOT + ".mapper.error");

    @ArchTest
    static final ArchRule mapper_error_holds_only_exception_mappers = topLevelClasses()
            .and().resideInAPackage(BFF_IMPL_ROOT + ".mapper.error")
            .should().haveNameMatching(".*ExceptionToApiExceptionMapper(Impl)?");

    @ArchTest
    static final ArchRule delegate_impls_live_at_the_delegate_root = classes()
            .that().haveSimpleNameEndingWith("ApiDelegateImpl")
            .should().resideInAPackage(BFF_IMPL_ROOT);

    @ArchTest
    static final ArchRule delegate_root_holds_only_delegate_impls = topLevelClasses()
            .and().resideInAPackage(BFF_IMPL_ROOT)
            .should().haveSimpleNameEndingWith("ApiDelegateImpl");

    @ArchTest
    static final ArchRule controller_root_holds_only_controllers_and_delegates = topLevelClasses()
            .and().resideInAPackage(BFF_API_ROOT)
            .should().haveNameMatching(".*Api(Controller|Delegate)");

    @ArchTest
    static final ArchRule rows_live_in_row_packages = topLevelClasses()
            .and().haveSimpleNameEndingWith("Row")
            .should().resideInAPackage(DOMAIN_ADAPTER + "row..")
            .because("persistence rows (table shapes) live in adapter.<port>.row");

    @ArchTest
    static final ArchRule row_packages_hold_only_row_records = topLevelClasses()
            .and().resideInAPackage("..row..")
            .should().beAssignableTo(Record.class)
            .andShould().haveSimpleNameEndingWith("Row");

    @ArchTest
    static final ArchRule adapter_records_live_in_data_packages = topLevelClasses()
            .and().resideInAPackage(DOMAIN_ADAPTER)
            .and().areAssignableTo(Record.class)
            .should().resideInAnyPackage("..row..", "..json..", "..spec..")
            .because("rows, JSON shapes and runner specs each have a sub-package;"
                    + " an adapter package root holds adapters");

    @ArchTest
    static final ArchRule adapter_sub_packages_are_known_kinds = topLevelClasses()
            .and().resideInAPackage(DOMAIN_ADAPTER_PORT_SUB)
            .should().resideInAnyPackage("..mapper..", "..row..", "..json..", "..spec..", "..support..")
            .because("an adapter port package splits into mapper, row, json, spec and support only");

    @ArchTest
    static final ArchRule port_adapters_live_at_the_port_package_root = topLevelClasses()
            .and().resideInAPackage(DOMAIN_ADAPTER)
            .and().implement(resideInAPackage(DOMAIN_OUTBOUND))
            .should().resideInAPackage(DOMAIN_ADAPTER_PORT);

    @ArchTest
    static final ArchRule port_package_roots_hold_only_adapters = topLevelClasses()
            .and().resideInAPackage(DOMAIN_ADAPTER_PORT)
            .and().resideOutsideOfPackages(SHARED_ADAPTER_PACKAGES)
            .should().implement(resideInAPackage(DOMAIN_OUTBOUND))
            .because("rows, JSON shapes, mappers and helpers go into the port's sub-packages");

    @ArchTest
    static final ArchRule domain_core_sub_packages_are_known_kinds = topLevelClasses()
            .and().resideInAPackage(DOMAIN_CORE_SUB)
            .should().resideInAnyPackage(
                    "..core.inbound..", "..core.outbound..", "..core.service..", "..core.model..",
                    "..core.exception..");

    @ArchTest
    static final ArchRule orchestration_features_follow_the_domain_core_shape = topLevelClasses()
            .and().resideInAPackage(ORCHESTRATION_FEATURE_SUB)
            .should().resideInAnyPackage(
                    ORCHESTRATION + "inbound..", ORCHESTRATION + "service..", ORCHESTRATION + "model..")
            .because("an orchestration feature is shaped like a domain core: inbound, service, model");

    @ArchTest
    static final ArchRule use_cases_live_in_inbound_packages = classes()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().resideInAPackage(INBOUND);

    @ArchTest
    static final ArchRule services_live_in_service_packages = classes()
            .that().haveSimpleNameEndingWith("Service")
            .should().resideInAPackage(SERVICE);

    @ArchTest
    static final ArchRule exceptions_live_in_exception_packages = topLevelClasses()
            .and().areAssignableTo(Throwable.class)
            .should().resideInAnyPackage(CORE_EXCEPTION, BFF_API_ERROR);

    @ArchTest
    static final ArchRule error_codes_live_next_to_their_exception = topLevelClasses()
            .and().haveSimpleNameEndingWith("Error")
            .should().resideInAPackage(CORE_EXCEPTION);

    @ArchTest
    static final ArchRule exception_packages_hold_only_exceptions_and_error_codes = topLevelClasses()
            .and().resideInAnyPackage(CORE_EXCEPTION, BFF_API_ERROR)
            .should().beAssignableTo(Throwable.class)
            .orShould().haveSimpleNameEndingWith("Error")
            .orShould().beAnnotatedWith(RestControllerAdvice.class);

    // --- Mapping ---------------------------------------------------------------------------

    @ArchTest
    static final ArchRule mappers_live_at_layer_boundaries = classes()
            .that().areAnnotatedWith(Mapper.class)
            .should().resideInAnyPackage(DOMAIN_ADAPTER_MAPPER, BFF_IMPL_MAPPER)
            .because("mapping happens where data crosses a boundary, in that boundary's mapper package");

    @ArchTest
    static final ArchRule mappers_are_named_after_their_mapping = classes()
            .that().areAnnotatedWith(Mapper.class)
            .should().haveNameMatching(".*\\.[A-Z][A-Za-z0-9]*To[A-Z][A-Za-z0-9]*Mapper")
            .because("each mapper converts exactly one type into another: SourceToTargetMapper");

    @ArchTest
    static final ArchRule mapper_methods_are_named_map = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Mapper.class)
            .should().haveName("map");

    /** Top-level project classes, without {@code package-info} and generated nested types. */
    private static GivenClassesConjunction topLevelClasses() {
        return classes().that().areTopLevelClasses().and().doNotHaveSimpleName("package-info");
    }

    private static ArchCondition<JavaClass> implementOnlyProjectInterfacesThat(DescribedPredicate<JavaClass> allowed) {
        DescribedPredicate<JavaClass> projectClass = resideInAPackage(ROOT);
        return new ArchCondition<>("only implement project interfaces that " + allowed.getDescription()) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaClass implemented : javaClass.getAllRawInterfaces()) {
                    if (projectClass.test(implemented) && !allowed.test(implemented)) {
                        events.add(SimpleConditionEvent.violated(javaClass,
                                javaClass.getName() + " implements " + implemented.getName()));
                    }
                }
            }
        };
    }
}
