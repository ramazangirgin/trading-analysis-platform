plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "BFF delegate implementations"

dependencies {
    implementation(project(":backend:bff:api"))
    implementation(project(":backend:orchestration"))
    implementation(project(":backend:domain:analysis:core"))
    implementation(project(":backend:domain:report:core"))
    implementation(project(":backend:domain:catalog:core"))
    implementation(project(":backend:domain:settings:core"))
    implementation(project(":backend:domain:identity:core"))
    implementation(project(":backend:library:mapper"))
    implementation(libs.spring.aop)
    implementation(libs.spring.beans)
    implementation(libs.spring.context)
    implementation(libs.spring.core)
    implementation(libs.spring.web)
    implementation(libs.spring.webmvc)
    implementation(libs.jakarta.servlet.api)
    implementation(libs.spring.security.core)
    implementation(libs.spring.security.config)
    implementation(libs.spring.security.web)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
}
