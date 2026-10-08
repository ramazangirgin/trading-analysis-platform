package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** The primary key of {@link PresetEntity}: the {@code PresetId} value on column {@code ID}. */
@Embeddable
public class PresetIdEmbeddable implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "ID", nullable = false)
    private String value;

    public PresetIdEmbeddable() {}

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PresetIdEmbeddable that && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
