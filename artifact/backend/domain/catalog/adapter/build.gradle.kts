plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Catalog domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:catalog:core"))
}
