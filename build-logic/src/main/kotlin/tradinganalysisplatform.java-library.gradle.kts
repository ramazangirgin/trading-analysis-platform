plugins {
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion))
    }
}

base {
    // Unique jar names: several subprojects share simple names such as "core" or "api".
    archivesName.set(project.path.removePrefix(":").replace(':', '-'))
}

dependencies {
    implementation(platform(libs.findLibrary("spring-boot-bom").get()))

    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
