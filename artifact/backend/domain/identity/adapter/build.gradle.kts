plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Identity domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:identity:core"))
    implementation(libs.spring.context)
    implementation(libs.spring.data.jpa)
    implementation(libs.jakarta.persistence.api)
    implementation(libs.hibernate.core)
    implementation(libs.spring.security.crypto)
    implementation(libs.slf4j.api)

    testImplementation(testFixtures(project(":backend:library:persistence")))
}
