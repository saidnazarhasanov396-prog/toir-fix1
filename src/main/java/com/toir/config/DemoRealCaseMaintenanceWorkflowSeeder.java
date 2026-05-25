package com.toir.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(13)
@Profile("dev & demo-seed")
@RequiredArgsConstructor
public class DemoRealCaseMaintenanceWorkflowSeeder implements CommandLineRunner {

    private static final String SCRIPT = "db/demo-seed/phase-3-maintenance-workflow.sql";

    private final DemoSeedSqlExecutor sqlExecutor;

    @Override
    public void run(String... args) {
        log.info("Demo real-case seed phase-3 started: {}", SCRIPT);
        sqlExecutor.executeScript(SCRIPT);
        log.info("Demo real-case seed phase-3 finished");
    }
}
