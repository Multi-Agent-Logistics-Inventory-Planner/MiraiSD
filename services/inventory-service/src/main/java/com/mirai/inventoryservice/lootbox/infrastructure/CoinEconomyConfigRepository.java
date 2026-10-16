package com.mirai.inventoryservice.lootbox.infrastructure;

import com.mirai.inventoryservice.lootbox.domain.CoinEconomyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoinEconomyConfigRepository extends JpaRepository<CoinEconomyConfig, Integer> {
}
