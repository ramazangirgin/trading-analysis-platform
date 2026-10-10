package tr.girgin.backend.trading.analysis.platform.library.persistence.fixture.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Fixture of EntitySchemaRuleTest: no {@code @Table}. */
// Fixture: the rule reads the annotations, nothing reads the fields, so they are not private-with-accessors.
@SuppressWarnings("checkstyle:VisibilityModifier")
@Entity
public class NoTableEntity {

    @Id
    String id;
}
