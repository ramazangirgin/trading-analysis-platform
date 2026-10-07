plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Settings domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:settings:core"))
    implementation(libs.spring.context)
    implementation(libs.spring.jdbc)
    implementation(libs.slf4j.api)

    testImplementation(libs.flyway.core)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.flyway.database.postgresql)
    testImplementation(libs.testcontainers.postgresql)
}
