package com.pm.inventoryservice.dtos.mappers;

import com.pm.inventoryservice.dtos.requests.BinRequestDTO;
import com.pm.inventoryservice.dtos.responses.BinResponseDTO;
import com.pm.inventoryservice.models.Bin;
import org.springframework.lang.NonNull;

public final class BinMapper {

    private BinMapper() {
        throw new IllegalStateException("Utility class");
    }

    @NonNull
    public static BinResponseDTO toDTO(@NonNull Bin bin) {
        BinResponseDTO responseDTO = new BinResponseDTO();
        if (bin.getId() != null) {
            responseDTO.setId(bin.getId().toString());
        }
        responseDTO.setBinCode(bin.getBinCode());
        return responseDTO;
    }

    @NonNull
    public static Bin toEntity(@NonNull BinRequestDTO requestDTO) {
        Bin bin = new Bin();
        bin.setBinCode(requestDTO.getBinCode());
        return bin;
    }
}


