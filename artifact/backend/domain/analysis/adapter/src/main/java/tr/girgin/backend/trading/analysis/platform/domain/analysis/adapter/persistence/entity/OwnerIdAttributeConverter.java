package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;

/** An {@link OwnerId} as the text of its column. Applied with {@code @Convert}, not automatically. */
@Converter
public class OwnerIdAttributeConverter implements AttributeConverter<OwnerId, String> {

    @Override
    public String convertToDatabaseColumn(OwnerId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public OwnerId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new OwnerId(dbData);
    }
}
