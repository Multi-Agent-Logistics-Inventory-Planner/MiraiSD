package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.lootbox.CoinEconomyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoinEconomyConfigRepository extends JpaRepository<CoinEconomyConfig, Integer> {
}
