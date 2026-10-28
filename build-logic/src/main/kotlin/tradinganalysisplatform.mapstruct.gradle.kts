plugins {
    id("tradinganalysisplatform.java-library")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("mapstruct").get())
    annotationProcessor(libs.findLibrary("mapstruct-processor").get())
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-Amapstruct.suppressGeneratorTimestamp=true")
}
