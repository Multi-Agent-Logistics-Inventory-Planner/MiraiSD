package com.mirai.inventoryservice.catalog.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

import java.io.IOException;

/**
 * Jackson support for deserializing a JSON property directly into a {@link FieldUpdate}: a
 * property absent from the request body leaves the field at its Java default
 * ({@link FieldUpdate#omitted()}, set by the field initializer - this deserializer is never
 * invoked for an absent property); a JSON {@code null} explicit-clears via {@link #getNullValue};
 * a present value is read as the wrapped type and wrapped with {@link FieldUpdate#of}. The
 * wrapped type is resolved per property via {@link ContextualDeserializer}, so one deserializer
 * instance serves every {@code FieldUpdate<T>} field regardless of {@code T}.
 * <p>
 * Replaces the hand-rolled {@code JsonNode} extraction {@code SiteProductController} used before
 * (phase-5d T-2 review): that approach had no typed request schema for springdoc/the generated
 * TypeScript client (it exported as an opaque {@code Record<string, never>}), and used
 * {@code JsonNode.asLong()} for {@code expectedVersion}, which silently coerces a non-numeric
 * string to {@code 0} instead of rejecting it - a version-zero row would then accept a write from
 * a caller who sent garbage. A typed {@code Long expectedVersion} field deserialized the normal
 * way throws on a non-numeric value instead of coercing it.
 */
public class FieldUpdateDeserializer extends JsonDeserializer<FieldUpdate<?>> implements ContextualDeserializer {

    private final JavaType valueType;

    public FieldUpdateDeserializer() {
        this.valueType = null;
    }

    private FieldUpdateDeserializer(JavaType valueType) {
        this.valueType = valueType;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType wrapperType = property != null ? property.getType() : ctxt.getContextualType();
        return new FieldUpdateDeserializer(wrapperType.containedType(0));
    }

    @Override
    public FieldUpdate<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        Object value = ctxt.readValue(p, valueType);
        return FieldUpdate.of(value);
    }

    @Override
    public FieldUpdate<?> getNullValue(DeserializationContext ctxt) {
        return FieldUpdate.of(null);
    }
}
