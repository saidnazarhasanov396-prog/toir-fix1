package com.toir.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(12)
@Profile("dev & demo-seed")
@RequiredArgsConstructor
public class DemoRealCaseEquipmentWarehouseSeeder implements CommandLineRunner {

    private static final String SCRIPT = "db/demo-seed/phase-2-equipment-warehouse.sql";

    private final DemoSeedSqlExecutor sqlExecutor;

    @Override
    public void run(String... args) {
        log.info("Demo real-case seed phase-2 started: {}", SCRIPT);
        sqlExecutor.executeScript(SCRIPT);
        log.info("Demo real-case seed phase-2 finished");
    }
}
