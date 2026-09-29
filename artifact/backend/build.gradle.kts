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
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.sqlite.jdbc)

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
    runtimeOnly(libs.springdoc.openapi.webmvc.api)

    frontend(project(path = ":frontend", configuration = "dist"))

    testImplementation(libs.archunit.junit5)
    testImplementation(libs.mapstruct)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.jackson.databind)
}

// Run from the repository root, so relative paths in application.properties (the ta-runner
// checkout, its .env) resolve the same as with `java -jar` from the root.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootDir
}

tasks.processResources {
    from(frontend) {
        into("static")
    }
}
