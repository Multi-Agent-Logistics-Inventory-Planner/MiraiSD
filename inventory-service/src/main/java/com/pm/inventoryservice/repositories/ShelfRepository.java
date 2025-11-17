package com.pm.inventoryservice.repositories;

import com.pm.inventoryservice.models.Shelf;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShelfRepository extends JpaRepository<Shelf, UUID> {

    boolean existsByShelfCode(String shelfCode);

    boolean existsByShelfCodeAndIdNot(String shelfCode, UUID shelfId);
}


