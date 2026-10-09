plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Settings domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:settings:core"))
    implementation(libs.spring.beans)
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.spring.data.commons)
    implementation(libs.spring.data.jpa)
    implementation(libs.jakarta.persistence.api)
    implementation(libs.flyway.core)

    testImplementation(testFixtures(project(":backend:library:persistence")))
}
