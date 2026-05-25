package com.toir.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(14)
@Profile("dev & demo-seed")
@RequiredArgsConstructor
public class DemoRealCaseFinanceDocsAuditSeeder implements CommandLineRunner {

    private static final String SCRIPT = "db/demo-seed/phase-4-finance-docs-audit.sql";

    private final DemoSeedSqlExecutor sqlExecutor;

    @Override
    public void run(String... args) {
        log.info("Demo real-case seed phase-4 started: {}", SCRIPT);
        sqlExecutor.executeScript(SCRIPT);
        log.info("Demo real-case seed phase-4 finished");
    }
}
