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
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

@AnalyzeClasses(packages = "tr.girgin.backend.trading.analysis.platform", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "tr.girgin.backend.trading.analysis.platform..";
    private static final String BFF = "tr.girgin.backend.trading.analysis.platform.bff..";
    private static final String BFF_API = "tr.girgin.backend.trading.analysis.platform.bff.controller.api..";
    private static final String BFF_IMPL = "tr.girgin.backend.trading.analysis.platform.bff.delegate.impl..";
    private static final String DOMAIN = "tr.girgin.backend.trading.analysis.platform.domain..";
    private static final String DOMAIN_CORE = "tr.girgin.backend.trading.analysis.platform.domain.*.core..";
    private static final String DOMAIN_MODEL = "tr.girgin.backend.trading.analysis.platform.domain.*.core.model..";
    private static final String DOMAIN_SERVICE = "tr.girgin.backend.trading.analysis.platform.domain.*.core.service..";
    private static final String DOMAIN_OUTBOUND = "tr.girgin.backend.trading.analysis.platform.domain.*.core.outbound..";
    private static final String DOMAIN_ADAPTER = "tr.girgin.backend.trading.analysis.platform.domain.*.adapter..";
    private static final String DOMAIN_RUNNER_ADAPTER = "tr.girgin.backend.trading.analysis.platform.domain.*.adapter.runner..";
    private static final String ORCHESTRATION = "tr.girgin.backend.trading.analysis.platform.orchestration..";

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

    // --- Mapping ---------------------------------------------------------------------------

    @ArchTest
    static final ArchRule mappers_live_at_layer_boundaries = classes()
            .that().areAnnotatedWith(Mapper.class)
            .should().resideInAnyPackage(DOMAIN_ADAPTER, BFF_IMPL)
            .because("mapping happens where data crosses a boundary");

    @ArchTest
    static final ArchRule mappers_are_named_after_their_mapping = classes()
            .that().areAnnotatedWith(Mapper.class)
            .should().haveNameMatching(".*\\.[A-Z][A-Za-z0-9]*To[A-Z][A-Za-z0-9]*Mapper")
            .because("each mapper converts exactly one type into another: SourceToTargetMapper");

    @ArchTest
    static final ArchRule mapper_methods_are_named_map = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Mapper.class)
            .should().haveName("map");

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
