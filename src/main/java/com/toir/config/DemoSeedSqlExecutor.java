package com.toir.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class DemoSeedSqlExecutor {

    private final DataSource dataSource;

    public void executeScript(String classpathLocation) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(false);
        populator.setIgnoreFailedDrops(true);
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ClassPathResource(classpathLocation));
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
