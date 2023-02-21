package io.cockroachdb.workload.common;

import java.util.Arrays;

import javax.sql.DataSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

public abstract class DatabasePopulator {
    private DatabasePopulator() {
    }

    public static void executeScripts(DataSource dataSource, String... paths) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        Arrays.stream(paths).sequential().forEach(p -> {
            populator.addScript(new ClassPathResource(p));
        });
        populator.setCommentPrefix("--");
        populator.setIgnoreFailedDrops(true);

        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
