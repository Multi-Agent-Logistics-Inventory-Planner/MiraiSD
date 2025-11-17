package com.pm.inventoryservice.dtos.mappers;

import com.pm.inventoryservice.dtos.requests.MachineRequestDTO;
import com.pm.inventoryservice.dtos.responses.MachineResponseDTO;
import com.pm.inventoryservice.models.Machine;
import org.springframework.lang.NonNull;

public class MachineMapper {
    private MachineMapper() {
        throw new IllegalStateException("Utility class");
    }

    @NonNull
    public static MachineResponseDTO toDTO(@NonNull Machine machine) {
        MachineResponseDTO machineDTO = new MachineResponseDTO();
        if (machine.getId() != null) {
            machineDTO.setId(machine.getId().toString());
        }
        machineDTO.setMachineCode(machine.getMachineCode());
        return machineDTO;
    }

    @NonNull
    public static Machine toEntity(@NonNull MachineRequestDTO machineRequestDTO) {
        Machine machine = new Machine();
        machine.setMachineCode(machineRequestDTO.getMachineCode());
        return machine;
    }
}
