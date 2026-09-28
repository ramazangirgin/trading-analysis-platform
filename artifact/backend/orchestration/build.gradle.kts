plugins {
    id("tradinganalysisplatform.java-library")
}

description = "Use cases spanning more than one domain"

dependencies {
    implementation(project(":backend:domain:analysis:core"))
    implementation(project(":backend:domain:report:core"))
    implementation(project(":backend:domain:catalog:core"))
    implementation(project(":backend:domain:settings:core"))
}
