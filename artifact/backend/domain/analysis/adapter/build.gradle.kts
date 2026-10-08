plugins {
    id("tradinganalysisplatform.mapstruct")
}

description = "Analysis domain: outbound adapters"

dependencies {
    implementation(project(":backend:domain:analysis:core"))
    implementation(project(":backend:library:mapper"))
    implementation(libs.spring.context)
    implementation(libs.spring.data.jpa)
    implementation(libs.jakarta.persistence.api)
    implementation(libs.hibernate.core)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)

    testImplementation(testFixtures(project(":backend:library:persistence")))
}
