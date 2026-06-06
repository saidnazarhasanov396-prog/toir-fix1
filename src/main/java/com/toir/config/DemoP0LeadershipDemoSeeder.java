package com.toir.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(15)
@Profile("dev & demo-seed")
@RequiredArgsConstructor
public class DemoP0LeadershipDemoSeeder implements CommandLineRunner {

    private static final String SCRIPT = "db/demo-seed/phase-5-p0-demo.sql";

    private final DemoSeedSqlExecutor sqlExecutor;

    @Override
    public void run(String... args) {
        log.info("P0 leadership demo seed phase-5 started: {}", SCRIPT);
        sqlExecutor.executeScript(SCRIPT);
        log.info("P0 leadership demo seed phase-5 finished");
    }
}
