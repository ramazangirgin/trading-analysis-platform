// Security pin (Dependabot), version and removal condition in gradle/libs.versions.toml. The version
// catalog accessors are not available in this block.
buildscript {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        constraints {
            classpath(libs.findLibrary("kotlin-gradle-plugin").get().get().let { "${it.module}:${it.versionConstraint.requiredVersion}" })
        }
    }
}

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.dependency.analyze.gradle.plugin)
}
