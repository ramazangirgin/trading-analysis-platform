plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Identity domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:identity:core"))
    implementation(libs.spring.beans)
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.spring.data.commons)
    implementation(libs.spring.data.jpa)
    implementation(libs.jakarta.persistence.api)
    implementation(libs.hibernate.core)
    implementation(libs.spring.security.crypto)

    testImplementation(testFixtures(project(":backend:library:persistence")))
}
