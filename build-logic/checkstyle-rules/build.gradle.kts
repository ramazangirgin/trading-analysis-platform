// The backend's own Checkstyle checks: generic, configurable checks that config/checkstyle/checkstyle.xml
// instantiates as project rules. An own build, included by the root settings.gradle.kts, so the
// backend's Checkstyle tasks can use the jar. See README.md.
plugins {
    `java-library`
    jacoco
    alias(libs.plugins.spotless)
}

// The coordinates the convention plugin uses: tradinganalysisplatform:checkstyle-rules.
group = "tradinganalysisplatform"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}

dependencies {
    // Not bundled: the Checkstyle that runs the checks supplies its own API.
    compileOnly(libs.checkstyle)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.checkstyle)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // The tests read the project's real Checkstyle config and the documentation.
    val repositoryRoot = rootDir.resolve("../..").canonicalFile
    systemProperty("repository.root", repositoryRoot.path)
    // The property is only a path: declare the files themselves, so changing them reruns the tests
    // instead of restoring an UP-TO-DATE or FROM-CACHE result.
    inputs
        .file(repositoryRoot.resolve("config/checkstyle/checkstyle.xml"))
        .withPropertyName("checkstyleConfig")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .file(repositoryRoot.resolve("docs/coding-convention/backend-java-checkstyle.md"))
        .withPropertyName("rulesCatalogue")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .file(layout.projectDirectory.file("README.md"))
        .withPropertyName("checksIndex")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .dir(layout.projectDirectory.dir("docs"))
        .withPropertyName("checkDocs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Every line, branch and instruction of the checks is covered. Code that cannot be reached is
// removed, not excluded.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            listOf("LINE", "BRANCH", "INSTRUCTION").forEach { counterType ->
                limit {
                    counter = counterType
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Like the backend (docs/coding-convention/backend-java-formatting.md). Not checked by Checkstyle itself:
// its config refers to these checks, which would be a cycle. The tests and the coverage gate are its gate.
spotless {
    providers.gradleProperty("spotlessRatchetFrom").orNull?.let { ratchetFrom(it) }
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat(libs.versions.palantirJavaFormat.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
