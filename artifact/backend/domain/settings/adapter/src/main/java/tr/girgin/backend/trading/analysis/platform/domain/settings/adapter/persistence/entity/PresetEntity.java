package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** One row of the {@code PRESETS} table. */
@Entity
@Table(name = "PRESETS")
public class PresetEntity {

    @EmbeddedId
    private PresetIdEmbeddable id;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "PAYLOAD", nullable = false)
    private String payload;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    public PresetEntity() {}

    public PresetIdEmbeddable getId() {
        return id;
    }

    public void setId(PresetIdEmbeddable id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
