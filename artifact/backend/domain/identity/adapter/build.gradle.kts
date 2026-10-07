plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Identity domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:identity:core"))
    implementation(libs.spring.context)
    implementation(libs.spring.jdbc)
    implementation(libs.spring.security.crypto)
    implementation(libs.slf4j.api)

    testImplementation(libs.flyway.core)
    testRuntimeOnly(libs.sqlite.jdbc)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.flyway.database.postgresql)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}
