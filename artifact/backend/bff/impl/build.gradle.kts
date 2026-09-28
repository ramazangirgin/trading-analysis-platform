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
}
