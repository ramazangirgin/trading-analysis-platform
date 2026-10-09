plugins {
    id("tradinganalysisplatform.java-library")
}

description = "Settings domain: model, ports and services"

dependencies {
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
}
