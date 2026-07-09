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
@Profile("navoiy-azot-seed & !test")
@RequiredArgsConstructor
public class NavoiyAzotOperationalSeedSeeder implements CommandLineRunner {

    private static final String[] SCRIPTS = {
            "db/navoiy-azot-seed/01_foundation_departments_locations.sql",
            "db/navoiy-azot-seed/02_employees_brigades.sql",
            "db/navoiy-azot-seed/03_warehouse_spare_parts_stock.sql",
            "db/navoiy-azot-seed/04_assets_equipment_vehicles.sql",
            "db/navoiy-azot-seed/05_maintenance_repair_workflow.sql",
            "db/navoiy-azot-seed/06_ppr_plans_tasks.sql",
            "db/navoiy-azot-seed/07_finance_budget_actual_costs.sql",
            "db/navoiy-azot-seed/99_validate_navoiy_azot_seed.sql"
    };

    private final ClasspathSqlScriptExecutor sqlExecutor;

    @Override
    public void run(String... args) {
        for (String script : SCRIPTS) {
            log.info("Navoiy Azot operational seed script started: {}", script);
            sqlExecutor.executeScript(script);
            log.info("Navoiy Azot operational seed script finished: {}", script);
        }
    }
}
