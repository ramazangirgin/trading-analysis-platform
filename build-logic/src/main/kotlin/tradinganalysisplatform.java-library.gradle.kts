plugins {
    `java-library`
    checkstyle
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

    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

// Spring resolves @PathVariable/@RequestParam names from parameter names. The Spring Boot
// plugin sets this only on the application project, not on the modules.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Checkstyle on main and test sources (checkstyleMain, checkstyleTest), part of `check` and so of
// `build`. One config for every module: config/checkstyle/checkstyle.xml.
// See doc/coding-convention/backend-java-checkstyle.md.
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
