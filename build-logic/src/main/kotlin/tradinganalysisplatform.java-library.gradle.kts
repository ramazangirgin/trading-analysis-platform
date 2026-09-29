plugins {
    `java-library`
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
