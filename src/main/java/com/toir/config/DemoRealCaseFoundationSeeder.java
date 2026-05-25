package com.toir.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(11)
@Profile("dev & demo-seed")
@RequiredArgsConstructor
public class DemoRealCaseFoundationSeeder implements CommandLineRunner {

    private static final String SCRIPT = "db/demo-seed/phase-1-foundation.sql";

    private final DemoSeedSqlExecutor sqlExecutor;

    @Override
    public void run(String... args) {
        log.info("Demo real-case seed phase-1 started: {}", SCRIPT);
        sqlExecutor.executeScript(SCRIPT);
        log.info("Demo real-case seed phase-1 finished");
    }
}
