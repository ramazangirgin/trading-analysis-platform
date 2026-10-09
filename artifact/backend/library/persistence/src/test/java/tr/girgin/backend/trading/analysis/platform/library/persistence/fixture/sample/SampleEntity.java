package tr.girgin.backend.trading.analysis.platform.library.persistence.fixture.sample;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Set;
import org.hibernate.annotations.ColumnTransformer;

/** The entity of the made-up domain "sample": mapped to its schema the way the convention asks. */
// Fixture: the rule reads the annotations, nothing reads the fields, so they are not private-with-accessors.
@SuppressWarnings("checkstyle:VisibilityModifier")
@Entity
@Table(name = "SAMPLES", schema = SamplePersistenceConfiguration.SCHEMA)
public class SampleEntity {

    @Id
    @Column(name = "ID")
    String id;

    @Column(name = "MOOD", columnDefinition = "\"SAMPLE\".\"SAMPLE_MOOD\"[]")
    @ColumnTransformer(write = "cast(? as \"SAMPLE\".\"SAMPLE_MOOD\"[])")
    String[] moods;

    @ElementCollection
    @CollectionTable(
            name = "SAMPLE_TAGS",
            schema = SamplePersistenceConfiguration.SCHEMA,
            joinColumns = @JoinColumn(name = "SAMPLE_ID"))
    @Column(name = "TAG")
    Set<String> tags;
}
