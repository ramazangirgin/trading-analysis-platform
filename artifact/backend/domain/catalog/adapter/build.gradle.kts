plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Catalog domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:catalog:core"))
    implementation(libs.spring.beans)
    implementation(libs.spring.context)
    implementation(libs.spring.core)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.docker.java.api)
    implementation(libs.docker.java.transport)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)
}
