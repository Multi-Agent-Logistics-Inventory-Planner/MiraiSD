package com.mirai.inventoryservice.validation;

import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class InventoryRequestValidator implements ConstraintValidator<ValidInventoryRequest, InventoryRequestDTO> {

    @Override
    public void initialize(ValidInventoryRequest constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(InventoryRequestDTO dto, ConstraintValidatorContext context) {
        if (dto == null || dto.getCategory() == null) {
            return true; // Let @NotNull handle null category
        }

        // Subcategory is ONLY required for BLIND_BOX
        // All other categories (PLUSHIE, KEYCHAIN, FIGURINE, GACHAPON, BUILD_KIT, GUNDAM, KUJI, MISC) don't need subcategory
        if (dto.getCategory() == ProductCategory.BLIND_BOX) {
            if (dto.getSubcategory() == null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Subcategory is required for BLIND_BOX category")
                        .addPropertyNode("subcategory")
                        .addConstraintViolation();
                return false;
            }
        } else {
            // For non-BLIND_BOX categories, subcategory should be null (ignore if provided)
            // We'll set it to null in the service layer
        }

        return true;
    }
}

