// Security pins (Dependabot) for the build script classpath (the plugins below and what they bring in);
// versions and removal conditions in gradle/libs.versions.toml. The version catalog accessors are not
// available in this block.
buildscript {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    fun pin(alias: String): String =
        libs.findLibrary(alias).get().get().let { "${it.module}:${it.versionConstraint.requiredVersion}" }
    dependencies {
        constraints {
            // Jackson 2 from the node-gradle plugin: 2.14.2.
            classpath(pin("jackson2-core"))
            classpath(pin("jackson2-databind"))
            // Jackson 3 from the Spring Boot Gradle plugin: 3.1.5.
            classpath(pin("jackson3-core"))
            classpath(pin("jackson3-databind"))
        }
    }
}

plugins {
    base
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.node) apply false
    // The dependency analysis refuses to run unless it is also applied to the root project. The
    // modules get it from the convention plugin (build-logic).
    alias(libs.plugins.dependency.analyze)
}

// The custom Checkstyle checks (build-logic/checkstyle-rules) are an included build: Gradle cannot run
// their tasks by path from here, so the root build has tasks that run them. Their tests, coverage gate
// and formatting are part of `./gradlew build` / `check`.
// See build-logic/checkstyle-rules/README.md.
val checkstyleRules = gradle.includedBuild("checkstyle-rules")

tasks.register("checkstyleRulesCheck") {
    group = "verification"
    description = "Tests, coverage gate and formatting of the custom Checkstyle checks."
    dependsOn(checkstyleRules.task(":check"))
}

tasks.check {
    dependsOn("checkstyleRulesCheck")
}

tasks.register("spotlessCheck") {
    description = "Checks the formatting of the custom Checkstyle checks (the subprojects have their own)."
    dependsOn(checkstyleRules.task(":spotlessCheck"))
}

tasks.register("spotlessApply") {
    description = "Formats the custom Checkstyle checks (the subprojects have their own)."
    dependsOn(checkstyleRules.task(":spotlessApply"))
}
