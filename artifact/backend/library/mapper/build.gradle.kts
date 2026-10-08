plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Shared library: generic MapStruct scalar mappers, no project types"

dependencies {
    // The generated mappers are Spring beans (@Component).
    implementation(libs.spring.context)
}
