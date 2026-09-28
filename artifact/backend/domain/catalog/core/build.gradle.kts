plugins {
    id("tradinganalysisplatform.java-library")
}

description = "Catalog domain: model, ports and services"

dependencies {
    implementation(libs.spring.context)
    implementation(libs.slf4j.api)
}
