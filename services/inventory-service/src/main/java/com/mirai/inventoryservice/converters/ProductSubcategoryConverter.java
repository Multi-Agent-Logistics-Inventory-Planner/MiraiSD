package com.mirai.inventoryservice.converters;

import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProductSubcategoryConverter implements AttributeConverter<ProductSubcategory, String> {

    @Override
    public String convertToDatabaseColumn(ProductSubcategory subcategory) {
        if (subcategory == null) return null;
        return switch (subcategory) {
            case DREAMS -> "Dreams";
            case POKEMON -> "Pokemon";
            case POPMART -> "Popmart";
            case SANRIO_SAN_X -> "Sanrio/San-X";
            case FIFTY_TWO_TOYS -> "52 Toys";
            case ROLIFE -> "Rolife";
            case TOY_CITY -> "Toy City";
            case MINISO -> "Miniso";
            case MISCELLANEOUS -> "Miscellaneous";
        };
    }

    @Override
    public ProductSubcategory convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        return switch (dbValue) {
            case "Dreams" -> ProductSubcategory.DREAMS;
            case "Pokemon" -> ProductSubcategory.POKEMON;
            case "Popmart" -> ProductSubcategory.POPMART;
            case "Sanrio/San-X" -> ProductSubcategory.SANRIO_SAN_X;
            case "52 Toys" -> ProductSubcategory.FIFTY_TWO_TOYS;
            case "Rolife" -> ProductSubcategory.ROLIFE;
            case "Toy City" -> ProductSubcategory.TOY_CITY;
            case "Miniso" -> ProductSubcategory.MINISO;
            case "Miscellaneous" -> ProductSubcategory.MISCELLANEOUS;
            default -> throw new IllegalArgumentException("Unknown ProductSubcategory: " + dbValue);
        };
    }
}
