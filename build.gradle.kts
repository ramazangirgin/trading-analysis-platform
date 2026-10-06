plugins {
    base
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.node) apply false
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
