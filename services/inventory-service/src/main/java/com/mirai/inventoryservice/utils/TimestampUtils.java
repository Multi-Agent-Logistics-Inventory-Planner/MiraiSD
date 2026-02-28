package com.mirai.inventoryservice.utils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Utility class for timestamp conversions in native SQL query results.
 */
public final class TimestampUtils {

    private TimestampUtils() {
        // Prevent instantiation
    }

    /**
     * Converts a database timestamp object to OffsetDateTime.
     * Handles both OffsetDateTime and Instant types that may be returned by different JDBC drivers.
     *
     * @param timestamp The timestamp object from native query result
     * @return OffsetDateTime representation, or null if input is null
     * @throws IllegalArgumentException if timestamp type is not supported
     */
    public static OffsetDateTime toOffsetDateTime(Object timestamp) {
        if (timestamp == null) {
            return null;
        }
        if (timestamp instanceof OffsetDateTime odt) {
            return odt;
        }
        if (timestamp instanceof Instant instant) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        throw new IllegalArgumentException("Unsupported timestamp type: " + timestamp.getClass());
    }
}
