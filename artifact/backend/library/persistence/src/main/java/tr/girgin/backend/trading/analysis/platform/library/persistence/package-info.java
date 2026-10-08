/**
 * Technical persistence code that more than one persistence adapter needs, for example a base type
 * for insert-only entities. Nothing here yet: code moves in when a second adapter needs it, never
 * copied. It depends on {@code jakarta.persistence-api} and {@code spring-data-jpa} only, with no
 * project type and no domain concept. The shared test code of the adapters is in the test fixtures
 * of this module.
 */
package tr.girgin.backend.trading.analysis.platform.library.persistence;
