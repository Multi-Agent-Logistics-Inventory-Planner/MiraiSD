package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.PusherMachine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PusherMachineRepository extends JpaRepository<PusherMachine, UUID> {
    Optional<PusherMachine> findByPusherMachineCode(String pusherMachineCode);
    boolean existsByPusherMachineCode(String pusherMachineCode);
}
