package com.mirai.inventoryservice.catalog.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Rejects a non-integral JSON number instead of silently truncating it (T-2 review follow-up).
 * Jackson's default {@code Long} deserialization accepts a JSON float like {@code 0.9} and
 * truncates it to {@code 0} - for {@code SiteProductSettingsRequest.expectedVersion}, that would
 * let a caller's typo (or a client bug) authorize a write against a genuine version-0 row, the
 * same class of problem the earlier fix for a non-numeric string ({@code "nonsense"}) addressed.
 * Scoped to this one field via {@code @JsonDeserialize}, not a global Jackson coercion-config
 * change, since nothing else in this codebase has asked for stricter numeric coercion.
 */
public class StrictLongDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return p.getLongValue();
        }
        return ctxt.reportInputMismatch(Long.class,
                "expectedVersion must be an integral number, not '%s'", p.getText());
    }
}
