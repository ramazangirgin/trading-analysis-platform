plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Report domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:report:core"))
}
