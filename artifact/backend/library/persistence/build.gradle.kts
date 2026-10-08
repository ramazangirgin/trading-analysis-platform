plugins {
    id("tradinganalysisplatform.java-library")
    `java-test-fixtures`
}

description = "Shared library: technical persistence code, and the test fixtures of the persistence adapters"

dependencies {
    // Test fixtures are a source set of their own: the platform of the convention plugin does not reach it.
    testFixturesImplementation(platform(libs.spring.boot.bom))

    // What every persistence adapter test needs: JPA on PostgreSQL, Flyway, a Testcontainers database.
    testFixturesApi(libs.spring.boot.starter.data.jpa)
    testFixturesApi(libs.spring.boot.test)
    testFixturesApi(libs.flyway.core)
    testFixturesApi(libs.flyway.database.postgresql)
    testFixturesApi(libs.postgresql)
    testFixturesApi(libs.testcontainers.postgresql)
}
