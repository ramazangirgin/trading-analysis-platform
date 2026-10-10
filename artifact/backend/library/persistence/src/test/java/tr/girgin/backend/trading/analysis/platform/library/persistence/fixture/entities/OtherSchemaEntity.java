package tr.girgin.backend.trading.analysis.platform.library.persistence.fixture.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fixture of EntitySchemaRuleTest: the schema of another, made-up domain "OTHER". */
// Fixture: the rule reads the annotations, nothing reads the fields, so they are not private-with-accessors.
@SuppressWarnings("checkstyle:VisibilityModifier")
@Entity
@Table(name = "OTHER", schema = "OTHER")
public class OtherSchemaEntity {

    @Id
    String id;
}
