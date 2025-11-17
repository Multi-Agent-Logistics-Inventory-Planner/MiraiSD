package com.pm.inventoryservice.dtos.mappers;

import com.pm.inventoryservice.dtos.requests.ShelfRequestDTO;
import com.pm.inventoryservice.dtos.responses.ShelfResponseDTO;
import com.pm.inventoryservice.models.Shelf;
import org.springframework.lang.NonNull;

public final class ShelfMapper {

    private ShelfMapper() {
        throw new IllegalStateException("Utility class");
    }

    @NonNull
    public static ShelfResponseDTO toDTO(@NonNull Shelf shelf) {
        ShelfResponseDTO responseDTO = new ShelfResponseDTO();
        if (shelf.getId() != null) {
            responseDTO.setId(shelf.getId().toString());
        }
        responseDTO.setShelfCode(shelf.getShelfCode());
        return responseDTO;
    }

    @NonNull
    public static Shelf toEntity(@NonNull ShelfRequestDTO requestDTO) {
        Shelf shelf = new Shelf();
        shelf.setShelfCode(requestDTO.getShelfCode());
        return shelf;
    }
}


