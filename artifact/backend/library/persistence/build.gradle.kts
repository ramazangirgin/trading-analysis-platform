plugins {
    id("tradinganalysisplatform.java-library")
    `java-test-fixtures`
}

description = "Shared library: technical persistence code, and the test fixtures of the persistence adapters"

dependencies {
    // JPA auditing: the configuration enables it, the date time provider is a Spring Data type.
    api(libs.spring.data.jpa)
    api(libs.spring.data.commons)
    implementation(libs.spring.context)

    // The fixture entities and configuration that prove the conventions test (src/test, never shipped).
    testImplementation(libs.jakarta.persistence.api)
    testImplementation(libs.hibernate.core)
    // Test fixtures are a source set of their own: the platform of the convention plugin does not reach it.
    testFixturesImplementation(platform(libs.spring.boot.bom))

    // What every persistence adapter test needs: JPA on PostgreSQL, Flyway, a Testcontainers database.
    testFixturesApi(libs.spring.boot.autoconfigure)
    testFixturesApi(libs.spring.boot.data.jpa)
    testFixturesApi(libs.spring.boot.flyway)
    testFixturesApi(libs.spring.boot.hibernate)
    testFixturesApi(libs.spring.boot.jdbc)
    testFixturesApi(libs.spring.boot.transaction)
    testFixturesApi(libs.spring.test)
    testFixturesImplementation(libs.spring.context)
    testFixturesApi(libs.flyway.core)
    // The conventions test of a domain: a JUnit class, and the entity rule that reads the domain's classes.
    testFixturesApi(libs.junit.jupiter.api)
    testFixturesApi(libs.archunit)
    testFixturesApi(libs.postgresql)
    testFixturesApi(libs.testcontainers.postgresql)
    // Not used by the fixtures' code, needed by the adapter tests at runtime: the JPA starter (Hibernate,
    // Hikari, auto-configuration) and the Flyway PostgreSQL support.
    testFixturesRuntimeOnly(libs.spring.boot.starter.data.jpa)
    testFixturesRuntimeOnly(libs.flyway.database.postgresql)
}
