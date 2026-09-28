plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Analysis domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:analysis:core"))
}
