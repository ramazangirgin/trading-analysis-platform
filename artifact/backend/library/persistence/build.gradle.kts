plugins {
    id("tradinganalysisplatform.java-library")
    `java-test-fixtures`
}

description = "Shared library: technical persistence code, and the test fixtures of the persistence adapters"

dependencies {
    // Test fixtures are a source set of their own: the platform of the convention plugin does not reach it.
    testFixturesImplementation(platform(libs.spring.boot.bom))

    // What every persistence adapter test needs: JPA on PostgreSQL, Flyway, a Testcontainers database.
    testFixturesApi(libs.spring.boot.autoconfigure)
    testFixturesApi(libs.spring.boot.data.jpa)
    testFixturesApi(libs.spring.boot.hibernate)
    testFixturesApi(libs.spring.boot.transaction)
    testFixturesApi(libs.spring.test)
    testFixturesApi(libs.flyway.core)
    testFixturesApi(libs.postgresql)
    testFixturesApi(libs.testcontainers.postgresql)
    // Not used by the fixtures' code, needed by the adapter tests at runtime: the JPA starter (Hibernate,
    // Hikari, auto-configuration) and the Flyway PostgreSQL support.
    testFixturesRuntimeOnly(libs.spring.boot.starter.data.jpa)
    testFixturesRuntimeOnly(libs.flyway.database.postgresql)
}
