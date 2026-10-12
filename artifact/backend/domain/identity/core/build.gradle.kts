plugins {
    id("tradinganalysisplatform.java-library")
}

description = "Identity domain: users, roles, permissions and ports"

dependencies {
    implementation(libs.slf4j.api)
    implementation(libs.spring.beans)
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
}
