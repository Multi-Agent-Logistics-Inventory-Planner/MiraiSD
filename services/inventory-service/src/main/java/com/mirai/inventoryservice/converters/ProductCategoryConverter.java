package com.mirai.inventoryservice.converters;

import com.mirai.inventoryservice.models.enums.ProductCategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProductCategoryConverter implements AttributeConverter<ProductCategory, String> {

    @Override
    public String convertToDatabaseColumn(ProductCategory category) {
        if (category == null) return null;
        return switch (category) {
            case PLUSHIE -> "Plushie";
            case KEYCHAIN -> "Keychain";
            case FIGURINE -> "Figurine";
            case GACHAPON -> "Gachapon";
            case BLIND_BOX -> "Blind Box";
            case BUILD_KIT -> "Build Kit";
            case GUNDAM -> "Gundam";
            case KUJI -> "Kuji";
            case MISCELLANEOUS -> "Miscellaneous";
        };
    }

    @Override
    public ProductCategory convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        return switch (dbValue) {
            case "Plushie" -> ProductCategory.PLUSHIE;
            case "Keychain" -> ProductCategory.KEYCHAIN;
            case "Figurine" -> ProductCategory.FIGURINE;
            case "Gachapon" -> ProductCategory.GACHAPON;
            case "Blind Box" -> ProductCategory.BLIND_BOX;
            case "Build Kit" -> ProductCategory.BUILD_KIT;
            case "Gundam" -> ProductCategory.GUNDAM;
            case "Kuji" -> ProductCategory.KUJI;
            case "Miscellaneous" -> ProductCategory.MISCELLANEOUS;
            default -> throw new IllegalArgumentException("Unknown ProductCategory: " + dbValue);
        };
    }
}
