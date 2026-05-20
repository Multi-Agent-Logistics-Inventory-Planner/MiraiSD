package com.mirai.inventoryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Random;

@Configuration
@ConfigurationProperties(prefix = "lootbox")
public class LootboxConfig {

    private LocalDate launchDate;

    public LocalDate getLaunchDate() {
        return launchDate;
    }

    public void setLaunchDate(LocalDate launchDate) {
        this.launchDate = launchDate;
    }

    @Bean
    public Random lootboxRandom() {
        return new SecureRandom();
    }
}
