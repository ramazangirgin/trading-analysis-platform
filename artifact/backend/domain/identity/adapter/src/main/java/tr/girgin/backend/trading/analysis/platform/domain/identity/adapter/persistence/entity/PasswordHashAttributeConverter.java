package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;

/** A {@link PasswordHash} as the text of its column. Applied with {@code @Convert}, not automatically. */
@Converter
public class PasswordHashAttributeConverter implements AttributeConverter<PasswordHash, String> {

    @Override
    public String convertToDatabaseColumn(PasswordHash attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public PasswordHash convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new PasswordHash(dbData);
    }
}
