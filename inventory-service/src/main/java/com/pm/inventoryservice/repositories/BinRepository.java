package com.pm.inventoryservice.repositories;

import com.pm.inventoryservice.models.Bin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BinRepository extends JpaRepository<Bin, UUID> {

    boolean existsByBinCode(String binCode);

    boolean existsByBinCodeAndIdNot(String binCode, UUID binId);
}


