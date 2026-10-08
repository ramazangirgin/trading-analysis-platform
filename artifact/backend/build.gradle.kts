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
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
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
    runtimeOnly(libs.springdoc.openapi.webmvc.api)

    frontend(project(path = ":frontend", configuration = "dist"))

    testImplementation(libs.archunit.junit5)
    testImplementation(libs.mapstruct)
    testImplementation(libs.spring.boot.starter.webmvc.test)
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

tasks.processResources {
    from(frontend) {
        into("static")
    }
}
