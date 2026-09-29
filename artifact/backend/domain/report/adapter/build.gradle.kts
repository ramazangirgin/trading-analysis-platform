plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Report domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:report:core"))
    implementation(libs.spring.context)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
}
