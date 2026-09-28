plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Settings domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:settings:core"))
}
