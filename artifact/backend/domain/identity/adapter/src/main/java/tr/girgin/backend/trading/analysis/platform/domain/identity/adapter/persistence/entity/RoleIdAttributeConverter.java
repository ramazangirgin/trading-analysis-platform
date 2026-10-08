package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;

/**
 * A {@link RoleId} as the text of the {@code ROLE_ID} column of {@code USER_ROLES}. Applied with
 * {@code @Convert}, not automatically, and never on an ID: a primary key is an embeddable.
 */
@Converter
public class RoleIdAttributeConverter implements AttributeConverter<RoleId, String> {

    @Override
    public String convertToDatabaseColumn(RoleId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public RoleId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new RoleId(dbData);
    }
}
