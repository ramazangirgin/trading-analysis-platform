plugins {
    id("tradinganalysisplatform.java-library")
}

description = "Identity domain: users, roles, permissions and ports"

dependencies {
    implementation(libs.spring.context)
    implementation(libs.slf4j.api)
}
