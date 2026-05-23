package com.mirai.inventoryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Lootbox-feature wiring. Used to also hold a launch_date that filtered the review
 * balance query, but V43 introduced expires_at so that floor is no longer needed —
 * old rows expire naturally.
 */
@Configuration
public class LootboxConfig {

    @Bean
    public Random lootboxRandom() {
        return new SecureRandom();
    }
}
