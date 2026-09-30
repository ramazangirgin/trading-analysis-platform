plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Catalog domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:catalog:core"))
    implementation(libs.spring.context)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)
}
