plugins {
    id("tradinganalysisplatform.java-library")
}

description = "BFF REST controllers, delegate interfaces and web DTOs"

dependencies {
    // Libraries, not starters: the application (:backend) brings the starters.
    api(libs.spring.core)
    api(libs.spring.context)
    api(libs.spring.web)
    api(libs.spring.webmvc)
    api(libs.jakarta.validation.api)
    // The security filter chain (config/SecurityConfig) and the beans AuthApiDelegateImpl shares with it.
    implementation(libs.spring.beans)
    implementation(libs.jackson.databind)
    api(libs.jakarta.servlet.api)
    api(libs.spring.security.core)
    api(libs.spring.security.config)
    api(libs.spring.security.web)
}
