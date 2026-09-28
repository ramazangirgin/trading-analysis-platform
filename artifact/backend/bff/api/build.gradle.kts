plugins {
    id("tradinganalysisplatform.java-library")
}

description = "BFF REST controllers, delegate interfaces and web DTOs"

dependencies {
    api(libs.spring.boot.starter.webmvc)
}
