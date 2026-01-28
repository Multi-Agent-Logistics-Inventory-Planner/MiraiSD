package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.FourCornerMachine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FourCornerMachineRepository extends JpaRepository<FourCornerMachine, UUID> {
    Optional<FourCornerMachine> findByFourCornerMachineCode(String fourCornerMachineCode);
    boolean existsByFourCornerMachineCode(String fourCornerMachineCode);
}
