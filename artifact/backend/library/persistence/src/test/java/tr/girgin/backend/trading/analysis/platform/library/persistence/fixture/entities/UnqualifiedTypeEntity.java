package tr.girgin.backend.trading.analysis.platform.library.persistence.fixture.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fixture of EntitySchemaRuleTest: an enum type in {@code columnDefinition} without the schema. */
// Fixture: the rule reads the annotations, nothing reads the fields, so they are not private-with-accessors.
@SuppressWarnings("checkstyle:VisibilityModifier")
@Entity
@Table(name = "UNQUALIFIED_TYPE", schema = "SAMPLE")
public class UnqualifiedTypeEntity {

    @Id
    String id;

    @Column(name = "STATUS", columnDefinition = "\"SAMPLE_STATUS\"")
    String status;
}
