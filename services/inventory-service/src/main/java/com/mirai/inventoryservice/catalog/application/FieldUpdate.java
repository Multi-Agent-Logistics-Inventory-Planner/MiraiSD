package com.mirai.inventoryservice.catalog.application;

import java.util.Objects;

/**
 * Distinguishes "this field was not part of the request" (leave the stored value unchanged) from
 * "this field was explicitly included, and its value - possibly {@code null} - is what the
 * caller wants stored." A plain nullable field cannot express this distinction, which AC-6
 * (.specs/phase-5c-site-products) requires for {@link SiteProductSettingsUpdate}: an explicit
 * {@code null} clears an override back to inheriting from the global product, while an omitted
 * field must not touch it.
 */
public final class FieldUpdate<T> {

    private static final FieldUpdate<?> OMITTED = new FieldUpdate<>(false, null);

    private final boolean present;
    private final T value;

    private FieldUpdate(boolean present, T value) {
        this.present = present;
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public static <T> FieldUpdate<T> omitted() {
        return (FieldUpdate<T>) OMITTED;
    }

    /** {@code value} may itself be {@code null} - that is an explicit clear, not an omission. */
    public static <T> FieldUpdate<T> of(T value) {
        return new FieldUpdate<>(true, value);
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isOmitted() {
        return !present;
    }

    public T value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FieldUpdate<?> other)) {
            return false;
        }
        return present == other.present && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(present, value);
    }

    @Override
    public String toString() {
        return present ? "FieldUpdate[" + value + "]" : "FieldUpdate.omitted";
    }
}
