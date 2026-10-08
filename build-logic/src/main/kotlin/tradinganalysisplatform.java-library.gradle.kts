plugins {
    `java-library`
    checkstyle
    id("com.diffplug.spotless")
    id("ca.cutterslade.analyze")
}

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion))
    }
}

// Several subprojects share simple names ("core", "adapter", "api"). With a shared group they
// would get identical module coordinates, and Gradle would resolve them as one module.
group = "${project.group}" + project.path.replace(':', '.')

base {
    // Unique jar names: several subprojects share simple names such as "core" or "api".
    archivesName.set(project.path.removePrefix(":").replace(':', '-'))
}

dependencies {
    implementation(platform(libs.findLibrary("spring-boot-bom").get()))

    // Security pins (Dependabot), versions and removal conditions in gradle/libs.versions.toml.
    // Jackson 2 (docker-java-core, swagger-core-jakarta): the BOM aligns every Jackson 2 module.
    implementation(platform(libs.findLibrary("jackson2-bom").get()))

    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())

    // The project's own Checkstyle checks (build-logic/checkstyle-rules, an included build), on the
    // classpath of checkstyleMain / checkstyleTest. Declaring any dependency replaces the plugin's
    // default one, so Checkstyle itself is declared too (same version as toolVersion below).
    add("checkstyle", libs.findLibrary("checkstyle").get())
    add("checkstyle", "tradinganalysisplatform:checkstyle-rules")
}

// Dependency analysis (gradle-dependency-analyze): the build fails on a library the code uses but the
// module does not declare, and on one it declares but does not use. Part of `check`. The plugin
// fails by default; exceptions go into its permit* configurations, in the build file of the module.
// See docs/coding-convention/repository-dependency-hygiene.md.
tasks.withType<ca.cutterslade.gradle.analyze.AnalyzeDependenciesTask>().configureEach {
    warnUsedUndeclared = false
    warnUnusedDeclared = false
    warnSuperfluous = false
    logDependencyInformationToFiles = true
}

// The plugin makes its tasks depend on every task of the module that produces classes or a jar: the
// tests, Checkstyle and (in :backend) bootTestRun, which fails without a database. The analysis needs
// the compiled classes only; the classpath inputs bring the other modules' jars on their own.
afterEvaluate {
    tasks.named("analyzeClassesDependencies") {
        setDependsOn(listOf("compileJava", "classes"))
    }
    tasks.named("analyzeTestClassesDependencies") {
        setDependsOn(listOf("compileJava", "classes", "compileTestJava", "testClasses"))
    }
    // A module without tests declares the test starter (every module gets it) but cannot use it.
    // Not permitted for modules with tests: there the permit would hide the starter from the aggregator.
    if (sourceSets["test"].allSource.isEmpty) {
        dependencies.add("permitTestUnusedDeclared", libs.findLibrary("spring-boot-starter-test").get())
    }
}

dependencies {
    // The test starter is the one aggregator: tests use AssertJ, JUnit, Mockito and Spring Test
    // through it, and a module without tests does not fail on it.
    add("permitTestAggregatorUse", libs.findLibrary("spring-boot-starter-test").get())
}

// The plugin copies the `api` declarations into its own apiHelper configurations, and has its own permit*
// ones; none of them sees the BOM imported on `implementation`, so a starter would resolve without a version.
configurations.matching { it.name.startsWith("apiHelper") || it.name.startsWith("permit") }.configureEach {
    dependencies.add(project.dependencies.platform(libs.findLibrary("spring-boot-bom").get().get()))
}

// Spring resolves @PathVariable/@RequestParam names from parameter names. The Spring Boot
// plugin sets this only on the application project, not on the modules.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

// Testcontainers on a Podman machine (macOS, Windows): Ryuk, its cleanup container, mounts the
// DOCKER_HOST socket path, which is the host's path and does not exist inside the machine's VM
// ("read-only file system"). A rootful machine serves the API at /run/podman/podman.sock inside the
// VM, so Ryuk gets that path and keeps cleaning up; a rootless machine's path depends on its user,
// so Ryuk is switched off there. Docker, and Podman on Linux without a machine, need neither. A
// value set in the environment always wins.
val podmanMachineTestcontainersEnv: Map<String, String> by lazy {
    val dockerHost = providers.environmentVariable("DOCKER_HOST").orNull.orEmpty()
    val onPodmanMachine = dockerHost.contains("/podman/machine/") || dockerHost.contains("podman-machine")
    val alreadySet = listOf("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "TESTCONTAINERS_RYUK_DISABLED")
        .any { providers.environmentVariable(it).isPresent }
    if (!onPodmanMachine || alreadySet) {
        emptyMap()
    } else {
        val rootful = providers.exec {
            commandLine("podman", "machine", "inspect", "--format", "{{.Rootful}}")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim() == "true"
        if (rootful) {
            mapOf("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE" to "/run/podman/podman.sock")
        } else {
            mapOf("TESTCONTAINERS_RYUK_DISABLED" to "true")
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    environment(podmanMachineTestcontainersEnv)
}

// Checkstyle on main and test sources (checkstyleMain, checkstyleTest), part of `check` and so of
// `build`. One config for every module: config/checkstyle/checkstyle.xml.
// See docs/coding-convention/backend-java-checkstyle.md.
checkstyle {
    toolVersion = libs.findVersion("checkstyle").get().requiredVersion
    configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
    maxWarnings = 0
    isIgnoreFailures = false
}

sourceSets.configureEach {
    val sourceSet = this
    tasks.named<Checkstyle>(getTaskName("checkstyle", null)) {
        // .properties files too (UniqueProperties); Gradle passes only the Java sources by default.
        source(sourceSet.resources.matching { include("**/*.properties") })
        // Generated sources (MapStruct *Impl) are not ours to style.
        exclude { it.file.invariantSeparatorsPath.contains("/build/generated/") }
        // MagicNumber covers main sources only: tests spell out expected values.
        if (sourceSet.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            configProperties = mapOf("magicNumberSeverity" to "ignore")
        }
    }
}

// Formatting: Spotless with Palantir Java Format. spotlessCheck is part of `check` and so of
// `build`; spotlessApply (or `mise run format`) fixes what it reports.
// -PspotlessRatchetFrom=<ref> limits both to files changed since that Git ref (the pre-commit hook
// passes HEAD). See docs/coding-convention/backend-java-formatting.md.
spotless {
    providers.gradleProperty("spotlessRatchetFrom").orNull?.let { ratchetFrom(it) }
    java {
        // Hand-written sources only: generated ones (MapStruct *Impl) live under build/.
        target("src/*/java/**/*.java")
        palantirJavaFormat(libs.findVersion("palantirJavaFormat").get().requiredVersion)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
