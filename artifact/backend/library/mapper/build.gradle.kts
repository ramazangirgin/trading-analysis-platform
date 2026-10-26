plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Shared library: shared MapStruct configuration and generic scalar mappers, no project types"

dependencies {
    // The generated mappers are Spring beans (@Component).
    implementation(libs.spring.context)

    // SharedMapperConfigTest generates a mapper over made-up types.
    testAnnotationProcessor(libs.mapstruct.processor)
}
