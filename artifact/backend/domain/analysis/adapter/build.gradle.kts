plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Analysis domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:analysis:core"))
    implementation(libs.spring.context)
    implementation(libs.spring.jdbc)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testImplementation(libs.flyway.core)
    testRuntimeOnly(libs.sqlite.jdbc)
}
