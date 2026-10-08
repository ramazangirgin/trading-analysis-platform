plugins {
    id("tradinganalysisplatform.java-library")
}

description = "Catalog domain: model, ports and services"

dependencies {
    implementation(libs.spring.beans)
    implementation(libs.spring.context)
}
