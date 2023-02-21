package io.cockroachdb.workload.common.command;

import java.io.IOException;
import java.io.LineNumberReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.AbstractShellComponent;
import org.springframework.shell.standard.ShellMethod;

import io.cockroachdb.workload.common.ExecutorTemplate;

public abstract class AbstractCommand extends AbstractShellComponent {
    @Autowired
    private Console console;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ExecutorTemplate executorTemplate;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private boolean initialized;

    protected Console getConsole() {
        return console;
    }

    protected DataSource getDataSource() {
        return dataSource;
    }

    protected ExecutorTemplate getExecutorTemplate() {
        return executorTemplate;
    }

    public ApplicationEventPublisher getApplicationEventPublisher() {
        return applicationEventPublisher;
    }

    public Availability dataSourceCheck() {
        try (Connection c = getDataSource().getConnection()) {
            return Availability.available();
        } catch (SQLException e) {
            return Availability.unavailable(e.getMessage());
        }
    }

    public Availability initializedCheck() throws SQLException {
        return initialized
                ? Availability.available()
                : Availability.unavailable("Not initialized");
    }

    public Availability activeWorkersCheck() {
        int activeThreads = getExecutorTemplate().getThreadPoolExecutor().getActiveCount();
        return activeThreads > 0
                ? Availability.available()
                : Availability.unavailable("No active workers");
    }

    public Availability noActiveWorkersCheck() {
        return activeWorkersCheck().isAvailable()
                ? Availability.unavailable("Active workers")
                : Availability.available();
    }

    @ShellMethod(value = "Print workload SQL files")
    public void printSQL() {
        this.sqlFiles().forEach(resource -> {
            System.out.println(">> " + resource);

            EncodedResource encodedScript = new EncodedResource(resource, "UTF-8");

            try (LineNumberReader lnr = new LineNumberReader(encodedScript.getReader())) {
                String line = lnr.readLine();
                while (line != null) {
                    System.out.println(line);
                    line = lnr.readLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    protected List<Resource> sqlFiles() {
        return Collections.emptyList();
    }

    protected void onPostInit() {
        this.initialized = true;
        getApplicationEventPublisher().publishEvent(new InitializedEvent(this));
    }
}

