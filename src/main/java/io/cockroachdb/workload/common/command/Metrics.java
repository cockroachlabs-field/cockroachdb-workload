package io.cockroachdb.workload.common.command;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.hibernate.engine.jdbc.connections.internal.ConnectionProviderInitiator;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;
import org.springframework.util.ReflectionUtils;

import com.zaxxer.hikari.HikariDataSource;

import io.cockroachdb.workload.common.CallMetrics;
import jakarta.annotation.PostConstruct;

import static io.cockroachdb.workload.common.config.DataSourceConfig.SQL_TRACE_LOGGER;

@ShellComponent
@ShellCommandGroup("Metrics Commands")
public class Metrics extends AbstractCommand {
    private static final Semaphore mutex = new Semaphore(1);

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    @Autowired
    private CallMetrics callMetrics;

    private ScheduledFuture<?> metricsFuture;

    @PostConstruct
    public void init() {
        toggleMetrics(5);
    }

    private Runnable callMetricsPrinter() {
        return () -> {
            if (metricsFuture != null && getExecutorTemplate().hasActiveWorkers()) {
                try {
                    mutex.acquire();
                    getConsole().successf("%s", callMetrics.prettyPrintHeader());
                    getConsole().infof("%s", callMetrics.prettyPrintBody());
                    getConsole().warnf("%s", callMetrics.prettyPrintFooter());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    mutex.release();
                }
            }
        };
    }

    @ShellMethod(value = "Toggle metrics output to console", key = {"metrics", "m"})
    public void toggleMetrics(@ShellOption(help = "print interval in seconds", defaultValue = "5") int interval) {
        if (metricsFuture == null) {
            metricsFuture = scheduledExecutorService.scheduleAtFixedRate(callMetricsPrinter(),
                    interval, interval, TimeUnit.SECONDS);
            getConsole().infof("Metrics printing is ON with interval %d sec", interval);
        } else {
            metricsFuture.cancel(true);
            metricsFuture = null;
            getConsole().infof("Metrics printing is OFF");
        }
    }

    @ShellMethod(value = "Toggle SQL tracing output to 'workload.log'", key = {"trace", "t"})
    public void toggleTrace() {
        ch.qos.logback.classic.LoggerContext loggerContext = (ch.qos.logback.classic.LoggerContext) LoggerFactory
                .getILoggerFactory();
        ch.qos.logback.classic.Logger logger = loggerContext.getLogger(SQL_TRACE_LOGGER);
        if (logger.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.DEBUG)) {
            logger.setLevel(ch.qos.logback.classic.Level.TRACE);
            getConsole().info("SQL trace logging ENABLED");
            logger.trace("Enabled");
        } else {
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            logger.debug("Disabled");
            getConsole().info("SQL trace logging DISABLED");
        }
    }

    @ShellMethod(value = "Print system information", key = {"system-info", "si"})
    public void systemInfo() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        getConsole().successf(">> OS");
        getConsole().infof(" Arch: %s | OS: %s | Version: %s", os.getArch(), os.getName(), os.getVersion());
        getConsole().infof(" Available processors: %d", os.getAvailableProcessors());
        getConsole().infof(" Load avg: %f", os.getSystemLoadAverage());

        RuntimeMXBean r = ManagementFactory.getRuntimeMXBean();
        getConsole().successf(">> Runtime");
        getConsole().infof(" Uptime: %s", r.getUptime());
        getConsole().infof(" VM name: %s | Vendor: %s | Version: %s", r.getVmName(), r.getVmVendor(),
                r.getVmVersion());

        ThreadMXBean t = ManagementFactory.getThreadMXBean();
        getConsole().successf(">> Runtime");
        getConsole().infof(" Peak threads: %d", t.getPeakThreadCount());
        getConsole().infof(" Thread #: %d", t.getThreadCount());
        getConsole().infof(" Total started threads: %d", t.getTotalStartedThreadCount());

        Arrays.stream(t.getAllThreadIds()).sequential().forEach(value -> {
            getConsole().infof(" Thread (%d): %s %s", value,
                    t.getThreadInfo(value).getThreadName(),
                    t.getThreadInfo(value).getThreadState().toString()
            );
        });

        MemoryMXBean m = ManagementFactory.getMemoryMXBean();
        getConsole().successf(">> Memory");
        getConsole().infof(" Heap: %s", m.getHeapMemoryUsage().toString());
        getConsole().infof(" Non-heap: %s", m.getNonHeapMemoryUsage().toString());
        getConsole().infof(" Pending GC: %s", m.getObjectPendingFinalizationCount());
    }

    @ShellMethod(value = "Print datasource information", key = {"datasource-info", "di"})
    @ShellMethodAvailability("dataSourceCheck")
    public void dbInfo(@ShellOption(help = "print all JDBC metadata", defaultValue = "false") boolean verbose)
            throws SQLException {
        HikariDataSource ds = getDataSource().unwrap(HikariDataSource.class);

        getConsole().successf("HikariCP data source");
        getConsole().infof("\tjdbc url: %s", ds.getJdbcUrl());
        getConsole().infof("\tjdbc user: %s", ds.getUsername());
        getConsole().infof("\tmax pool size: %s", ds.getMaximumPoolSize());
        getConsole().infof("\tmin idle size: %s", ds.getMinimumIdle());
        getConsole().infof("\tidle timeout: %s", ds.getIdleTimeout());
        getConsole().infof("\tmax lifetime: %s", ds.getMaxLifetime());
        getConsole().infof("\tvalidation timeout: %s", ds.getValidationTimeout());
        getConsole().infof("\tconnection init: %s", ds.getConnectionInitSql());

        getConsole().successf("Database metadata:");
        try {
            getConsole().infof("\tdatabaseVersion: %s",
                    new JdbcTemplate(ds).queryForObject("select version()", String.class));
        } catch (DataAccessException e) {
        }

        try (Connection connection = DataSourceUtils.doGetConnection(ds)) {
            DatabaseMetaData metaData = connection.getMetaData();

            if (verbose) {
                Arrays.stream(ReflectionUtils.getDeclaredMethods(metaData.getClass())).sequential().forEach(method -> {
                    if (method.getParameterCount() == 0) {
                        try {
                            getConsole().infof("\t%s: %s", method.getName(),
                                    ReflectionUtils.invokeMethod(method, metaData));
                        } catch (Exception e) {
                            getConsole().error(e.toString());
                        }
                    }
                });
            } else {
                getConsole().infof("\tautoCommit: %s", connection.getAutoCommit());
                getConsole().infof("\tdatabaseProductName: %s", metaData.getDatabaseProductName());
                getConsole().infof("\tdatabaseMajorVersion: %s", metaData.getDatabaseMajorVersion());
                getConsole().infof("\tdatabaseMinorVersion: %s", metaData.getDatabaseMinorVersion());
                getConsole().infof("\tdatabaseProductVersion: %s", metaData.getDatabaseProductVersion());
                getConsole().infof("\tdriverMajorVersion: %s", metaData.getDriverMajorVersion());
                getConsole().infof("\tdriverMinorVersion: %s", metaData.getDriverMinorVersion());
                getConsole().infof("\tdriverName: %s", metaData.getDriverName());
                getConsole().infof("\tdriverVersion: %s", metaData.getDriverVersion());
                getConsole().infof("\tmaxConnections: %s", metaData.getMaxConnections());
                getConsole().infof("\tdefaultTransactionIsolation: %s", metaData.getDefaultTransactionIsolation());
                getConsole().infof("\ttransactionIsolation: %s", connection.getTransactionIsolation());
                getConsole().infof("\ttransactionIsolationName: %s",
                        ConnectionProviderInitiator.toIsolationNiceName(connection.getTransactionIsolation()));
            }
        } catch (SQLException ex) {
            getConsole().error(ex.toString());
        }
    }
}
