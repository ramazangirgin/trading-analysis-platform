plugins {
    id("tradinganalysisplatform.java-library")
    alias(libs.plugins.spring.boot)
}

description = "Spring Boot application assembling all modules and serving the frontend build"

// The Vue build output, published by :frontend. Bundled as static resources so one jar
// serves both the API and the UI.
val frontend = configurations.create("frontend") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(libs.spring.boot)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.context)

    // The starters: the application gets the auto-configuration (Tomcat, Jackson, Hibernate Validator,
    // JPA, Flyway, actuator) from them, the library modules declare libraries only.
    runtimeOnly(libs.spring.boot.starter)
    runtimeOnly(libs.spring.boot.starter.actuator)
    runtimeOnly(libs.spring.boot.starter.webmvc)
    runtimeOnly(libs.spring.boot.starter.validation)
    runtimeOnly(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)

    // Runtime only: the application assembles the modules but never compiles against them.
    runtimeOnly(project(":backend:bff:api"))
    runtimeOnly(project(":backend:bff:impl"))
    runtimeOnly(project(":backend:orchestration"))
    runtimeOnly(project(":backend:domain:analysis:core"))
    runtimeOnly(project(":backend:domain:analysis:adapter"))
    runtimeOnly(project(":backend:domain:report:core"))
    runtimeOnly(project(":backend:domain:report:adapter"))
    runtimeOnly(project(":backend:domain:catalog:core"))
    runtimeOnly(project(":backend:domain:catalog:adapter"))
    runtimeOnly(project(":backend:domain:settings:core"))
    runtimeOnly(project(":backend:domain:settings:adapter"))
    runtimeOnly(project(":backend:domain:identity:core"))
    runtimeOnly(project(":backend:domain:identity:adapter"))
    runtimeOnly(project(":backend:library:mapper"))
    runtimeOnly(project(":backend:library:persistence"))
    runtimeOnly(libs.springdoc.openapi.webmvc.api)

    frontend(project(path = ":frontend", configuration = "dist"))

    testImplementation(libs.archunit)
    testImplementation(libs.archunit.junit5.api)
    testRuntimeOnly(libs.archunit.junit5)
    testImplementation(libs.mapstruct)
    testImplementation(libs.spring.boot.webmvc.test)
    testRuntimeOnly(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.web)
    testImplementation(libs.spring.data.commons)
    testImplementation(libs.spring.data.jpa)
    testImplementation(libs.jakarta.persistence.api)
    testImplementation(libs.hibernate.core)
    testImplementation(libs.jackson.databind)
    testImplementation(testFixtures(project(":backend:library:persistence")))
}

springBoot {
    // META-INF/build-info.properties: the version at /actuator/info and in the logs at startup. No
    // build time, so an unchanged build stays up to date and cacheable.
    buildInfo {
        excludes = setOf("time")
        properties {
            name = "TradingAgents Platform"
        }
    }
}

// A fixed name, so the Dockerfile, CI and mise tasks need no version; the release workflow attaches
// it as trading-analysis-platform-<version>.jar. The version is in its manifest and build info.
tasks.bootJar {
    archiveFileName = "platform.jar"
    manifest {
        attributes("Implementation-Title" to "TradingAgents Platform", "Implementation-Version" to project.version)
    }
}

// Run from the repository root, so relative paths in application.properties (the ta-runner
// checkout, its .env) resolve the same as with `java -jar` from the root.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootDir
}

// Every domain with persistence must prove its schema rules: a test class extending
// DomainPersistenceConventionsTest (docs/coding-convention/backend-java-persistence.md). A domain has
// persistence when its adapter holds an @Entity or a migration under adapter/persistence/migration.
abstract class DomainPersistenceTestsCheck : DefaultTask() {

    /** The adapter project's name (e.g. :backend:domain:analysis:adapter) and its directory. */
    @get:Internal
    abstract val adapters: MapProperty<String, Directory>

    // The result depends on where a file is (its adapter, its folder), not only on its content.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val sources: ConfigurableFileCollection

    /** Written on success: a task without outputs is never up to date. */
    @get:OutputFile
    abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val problems = mutableListOf<String>()
        adapters.get().toSortedMap().forEach { (name, directory) ->
            val root = directory.asFile
            val entity = root.resolve("src/main/java").walk()
                .firstOrNull { it.isFile && it.extension == "java" && Regex("^\\s*@(jakarta\\.persistence\\.)?Entity\\b", RegexOption.MULTILINE).containsMatchIn(it.readText()) }
            val migration = root.resolve("src/main/resources").walk()
                .firstOrNull { it.isFile && it.extension == "sql" && it.parentFile.path.endsWith("adapter/persistence/migration") }
            val reason = when {
                entity != null -> "@Entity in ${entity.name}"
                migration != null -> "migration ${migration.name}"
                else -> null
            }
            if (reason != null) {
                val hasTest = root.resolve("src/test/java").walk()
                    .any { it.isFile && it.extension == "java" && Regex("extends\\s+DomainPersistenceConventionsTest\\b").containsMatchIn(it.readText()) }
                if (!hasTest) {
                    problems += "$name has persistence ($reason) but no test extending DomainPersistenceConventionsTest"
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(problems.joinToString("\n"))
        }
        marker.get().asFile.writeText("ok\n")
    }
}

val domainPersistenceTestsCheck = tasks.register<DomainPersistenceTestsCheck>("domainPersistenceTestsCheck") {
    group = "verification"
    description = "Fails a domain adapter with persistence but no test extending DomainPersistenceConventionsTest"
    marker = layout.buildDirectory.file("domainPersistenceTestsCheck/ok")
    rootProject.subprojects
        .filter { it.path.matches(Regex(":backend:domain:[^:]+:adapter")) }
        .forEach { adapter ->
            adapters.put(adapter.path, adapter.layout.projectDirectory)
            sources.from(adapter.layout.projectDirectory.dir("src/main/java"))
            sources.from(adapter.layout.projectDirectory.dir("src/main/resources"))
            sources.from(adapter.layout.projectDirectory.dir("src/test/java"))
        }
}

tasks.named("check") {
    dependsOn(domainPersistenceTestsCheck)
}

tasks.processResources {
    from(frontend) {
        into("static")
    }
}
