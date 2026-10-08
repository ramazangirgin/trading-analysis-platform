plugins {
    id("tradinganalysisplatform.java-library")
}

description = "Report domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:report:core"))
    implementation(libs.spring.beans)
    implementation(libs.spring.context)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
}
