package tr.girgin.backend.trading.analysis.platform;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

// ConstantName: ArchUnit reports a rule by its field name, so rule names are snake_case sentences.
// DeclarationOrder: the package names come first, then the rules that use them.
// HideUtilityClassConstructor: JUnit instantiates the test class.
@SuppressWarnings({"checkstyle:ConstantName", "checkstyle:DeclarationOrder", "checkstyle:HideUtilityClassConstructor"})
@AnalyzeClasses(
        packages = "tr.girgin.backend.trading.analysis.platform",
        importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class OrchestrationArchitectureTest {

    private static final String ORCHESTRATION = "tr.girgin.backend.trading.analysis.platform.orchestration..";
    private static final String ORCHESTRATION_FEATURE_SUB =
            "tr.girgin.backend.trading.analysis.platform.orchestration.*.*..";
    private static final String ORCHESTRATION_INBOUND =
            "tr.girgin.backend.trading.analysis.platform.orchestration.*.inbound..";
    private static final String ORCHESTRATION_CONFIG =
            "tr.girgin.backend.trading.analysis.platform.orchestration.*.config..";

    @ArchTest
    static final ArchRule orchestration_features_follow_the_domain_core_shape = topLevelClasses()
            .and()
            .resideInAPackage(ORCHESTRATION_FEATURE_SUB)
            .should()
            .resideInAnyPackage(ORCHESTRATION + "inbound..", ORCHESTRATION + "model..", ORCHESTRATION + "config..")
            .because("an orchestration feature holds its concrete use cases in inbound, their types in model"
                    + " and its configuration properties in config");

    @ArchTest
    static final ArchRule orchestration_use_cases_are_concrete_beans = classes()
            .that()
            .resideInAPackage(ORCHESTRATION_INBOUND)
            .and()
            .haveSimpleNameEndingWith("UseCase")
            .should()
            .notBeInterfaces()
            .andShould()
            .beAnnotatedWith(Service.class)
            .because("an orchestration use case has a single implementation, so it is the bean itself");

    @ArchTest
    static final ArchRule orchestration_inbound_packages_hold_only_beans = topLevelClasses()
            .and()
            .resideInAPackage(ORCHESTRATION_INBOUND)
            .should()
            .beMetaAnnotatedWith(Component.class)
            .because("orchestration inbound holds beans only: a use case or its helper bean; configuration"
                    + " properties go to config, composite models to model");

    @ArchTest
    static final ArchRule orchestration_config_packages_hold_only_configuration_properties = topLevelClasses()
            .and()
            .resideInAPackage(ORCHESTRATION_CONFIG)
            .should()
            .beAnnotatedWith(ConfigurationProperties.class)
            .because("an orchestration config package holds configuration properties records only");

    @ArchTest
    static final ArchRule configuration_properties_live_in_config_packages = classes()
            .that()
            .areAnnotatedWith(ConfigurationProperties.class)
            .should()
            .resideInAPackage("..config..")
            .because("configuration properties are bound in a config package, away from beans and models");

    @ArchTest
    static final ArchRule use_cases_live_in_orchestration_inbound_packages = classes()
            .that()
            .haveSimpleNameEndingWith("UseCase")
            .should()
            .resideInAPackage(ORCHESTRATION_INBOUND)
            .because("UseCase names a concrete orchestration bean; a domain's inbound interface is a Port");

    /** Top-level project classes, without {@code package-info} and generated nested types. */
    private static GivenClassesConjunction topLevelClasses() {
        return classes().that().areTopLevelClasses().and().doNotHaveSimpleName("package-info");
    }
}
