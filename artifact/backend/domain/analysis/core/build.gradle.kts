plugins {
    id("tradinganalysisplatform.java-library")
}

description = "Analysis domain: model, ports and services"

dependencies {
    implementation(libs.spring.beans)
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.slf4j.api)
}
