/**
 * Technical persistence code that more than one persistence adapter needs: the JPA auditing
 * configuration ({@code JpaAuditingConfiguration}) and its time source ({@code ClockDateTimeProvider}).
 * More moves in when a second adapter needs it, never copied. It depends on libraries only
 * ({@code spring-data-jpa}, {@code spring-context}), with no project type and no domain concept. The
 * shared test code of the adapters is in the test fixtures of this module.
 */
package tr.girgin.backend.trading.analysis.platform.library.persistence;
