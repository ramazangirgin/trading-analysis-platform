package tr.girgin.backend.trading.analysis.platform.library.persistence.fixture.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Fixture of EntitySchemaRuleTest: a cast without the schema, and a column type of another, made-up domain's schema
 * ("OTHER").
 */
// Fixture: the rule reads the annotations, nothing reads the fields, so they are not private-with-accessors.
@SuppressWarnings("checkstyle:VisibilityModifier")
@Entity
@Table(name = "UNQUALIFIED_CAST", schema = "SAMPLE")
public class UnqualifiedCastEntity {

    @Id
    String id;

    @Column(name = "MOODS", columnDefinition = "\"OTHER\".\"SAMPLE_MOOD\"[]")
    @ColumnTransformer(write = "cast(? as \"SAMPLE_MOOD\"[])")
    String[] moods;
}
