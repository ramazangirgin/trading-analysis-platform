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
}
