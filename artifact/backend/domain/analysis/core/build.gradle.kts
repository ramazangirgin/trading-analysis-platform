plugins {
    id("tradinganalysisplatform.java-library")
    `java-test-fixtures`
}

description = "Analysis domain: model, ports and services, and the test fixture that builds analyses"

dependencies {
    implementation(libs.spring.beans)
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.slf4j.api)
}
