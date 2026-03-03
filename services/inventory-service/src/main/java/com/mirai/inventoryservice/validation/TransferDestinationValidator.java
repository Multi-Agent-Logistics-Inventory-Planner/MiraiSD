package com.mirai.inventoryservice.validation;

import com.mirai.inventoryservice.dtos.requests.TransferInventoryRequestDTO;
import com.mirai.inventoryservice.models.enums.LocationType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator that ensures a transfer request has a valid destination.
 * Either destinationInventoryId (for existing inventory) or destinationLocationId
 * (for creating new inventory at destination) must be provided,
 * unless the destination is NOT_ASSIGNED which has no physical location ID.
 */
public class TransferDestinationValidator
        implements ConstraintValidator<ValidTransferDestination, TransferInventoryRequestDTO> {

    @Override
    public boolean isValid(TransferInventoryRequestDTO request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        // NOT_ASSIGNED has no physical location, so no location ID is needed
        if (request.getDestinationLocationType() == LocationType.NOT_ASSIGNED) {
            return true;
        }

        boolean hasDestinationInventoryId = request.getDestinationInventoryId() != null;
        boolean hasDestinationLocationId = request.getDestinationLocationId() != null;

        return hasDestinationInventoryId || hasDestinationLocationId;
    }
}
