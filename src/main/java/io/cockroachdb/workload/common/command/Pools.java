package io.cockroachdb.workload.common.command;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.sql.SQLException;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.LongSummaryStatistics;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;

import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import jakarta.annotation.PostConstruct;

@ShellComponent
@ShellCommandGroup("Resource Pool Commands")
public class Pools extends AbstractCommand {
    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    private static final ConcurrentLinkedDeque<ConnectionPoolStats> aggregatedConnectionPoolStats
            = new ConcurrentLinkedDeque<>();

    private static final ConcurrentLinkedDeque<ThreadPoolStats> aggregatedThreadPoolStats
            = new ConcurrentLinkedDeque<>();

    private static final ConcurrentLinkedDeque<Double> aggregatedLoadAvg
            = new ConcurrentLinkedDeque<>();

    @PostConstruct
    public void init() {
        scheduledExecutorService.scheduleAtFixedRate(poolMetricsSampler(), 5, 1, TimeUnit.SECONDS);
    }

    private Runnable poolMetricsSampler() {
        return () -> {
            aggregatedThreadPoolStats.add(ThreadPoolStats.from(getExecutorTemplate().getThreadPoolExecutor()));
            OperatingSystemMXBean mxBean = ManagementFactory.getOperatingSystemMXBean();
            if (mxBean.getSystemLoadAverage() != -1) {
                aggregatedLoadAvg.add(mxBean.getSystemLoadAverage());
            }
            try {
                HikariDataSource ds = getDataSource().unwrap(HikariDataSource.class);
                aggregatedConnectionPoolStats.add(ConnectionPoolStats.from(ds.getHikariPoolMXBean()));
            } catch (SQLException e) {
                getConsole().warn(e.toString());
            }
        };
    }

    @ShellMethod(value = "Set connection and thread pool size", key = {"set-pool-size", "sps"})
    @ShellMethodAvailability("noActiveWorkersCheck")
    public void poolSize(
            @ShellOption(help = "connection pool max size (guide: 4x vCPUs / n:of pools)", defaultValue = "75")
            int maxSize,
            @ShellOption(help = "connection pool min idle size (same as max size)", defaultValue = "75")
            int minIdle,
            @ShellOption(help = "core thread pool size (default: 2x maxSize)", defaultValue = "-1") int coreThreads)
            throws
            SQLException {
        HikariDataSource ds = getDataSource().unwrap(HikariDataSource.class);

        getConsole().infof("Set max pool size to %d", maxSize);
        ds.setMaximumPoolSize(maxSize);

        getConsole().infof("Set min idle pool size to %d", minIdle);
        ds.setMinimumIdle(minIdle);

        if (coreThreads < 0) {
            coreThreads = maxSize * 2;
        }

        getConsole().infof("Set core thread pool size to %d", coreThreads);
        getExecutorTemplate().getThreadPoolExecutor().setCorePoolSize(coreThreads);
    }

    @ShellMethod(value = "Get connection and thread pool size", key = {"get-pool-size", "gps"})
    @ShellMethodAvailability("dataSourceCheck")
    public void poolSizeInfo(@ShellOption(help = "repeat period in seconds", defaultValue = "0") int repeatTime)
            throws SQLException {
        HikariDataSource ds = getDataSource().unwrap(HikariDataSource.class);

        Runnable r = () -> {
            ThreadPoolStats threadPoolStats = ThreadPoolStats.from(getExecutorTemplate().getThreadPoolExecutor());
            getConsole().successf(">> Thread Pool Status:");
            getConsole().infof("poolSize: %s", threadPoolStats.poolSize);
            getConsole().infof("maximumPoolSize: %s", threadPoolStats.maximumPoolSize);
            getConsole().infof("corePoolSize: %s", threadPoolStats.corePoolSize);
            getConsole().infof("activeCount: %s", threadPoolStats.activeCount);
            getConsole().infof("completedTaskCount: %s", threadPoolStats.completedTaskCount);
            getConsole().infof("taskCount: %s", threadPoolStats.taskCount);
            getConsole().infof("largestPoolSize: %s", threadPoolStats.largestPoolSize);

            HikariPoolMXBean hikariPoolMXBean = ds.getHikariPoolMXBean();
            getConsole().successf(">> Connection Pool Status:");
            getConsole().infof("activeConnections: %s", hikariPoolMXBean.getActiveConnections());
            getConsole().infof("idleConnections: %s", hikariPoolMXBean.getIdleConnections());
            getConsole().infof("totalConnections: %s", hikariPoolMXBean.getTotalConnections());
            getConsole().infof("threadsAwaitingConnection: %s", hikariPoolMXBean.getThreadsAwaitingConnection());

            HikariConfigMXBean hikariConfigMXBean = ds.getHikariConfigMXBean();
            getConsole().successf(">> Connection Pool Configuration:");
            getConsole().infof("maximumPoolSize: %s", hikariConfigMXBean.getMaximumPoolSize());
            getConsole().infof("minimumIdle: %s", hikariConfigMXBean.getMinimumIdle());
            getConsole().infof("connectionTimeout: %,d", hikariConfigMXBean.getConnectionTimeout());
            getConsole().infof("validationTimeout: %,d", hikariConfigMXBean.getValidationTimeout());
            getConsole().infof("idleTimeout: %,d", hikariConfigMXBean.getIdleTimeout());
            getConsole().infof("maxLifetime: %,d", hikariConfigMXBean.getMaxLifetime());
            getConsole().infof("poolName: %s", hikariConfigMXBean.getPoolName());
            getConsole().infof("leakDetectionThreshold: %s", hikariConfigMXBean.getLeakDetectionThreshold());
            getConsole().infof("catalog: %s", hikariConfigMXBean.getCatalog());

            if (threadPoolStats.corePoolSize < hikariConfigMXBean.getMaximumPoolSize()) {
                getConsole().warnf(
                        "Note: Thread pool size is smaller than connection pool size.");
            }
        };

        if (repeatTime > 0) {
            ScheduledFuture<?> f = scheduledExecutorService
                    .scheduleAtFixedRate(r, 0, 2, TimeUnit.SECONDS);
            scheduledExecutorService
                    .schedule(() -> {
                        f.cancel(true);
                    }, repeatTime, TimeUnit.SECONDS);
        } else {
            r.run();
        }
    }

    @ShellMethod(value = "Print connection and thread pool stats", key = {"pool-stats", "ps"})
    @ShellMethodAvailability("dataSourceCheck")
    public void poolStats(@ShellOption(help = "repeat period in seconds", defaultValue = "0") int repeatTime) {
        Runnable r = () -> {
            try {
                ThreadPoolStats threadPoolStats = aggregatedThreadPoolStats.peekLast();
                getConsole().warn(">> Pool Stats");
                if (threadPoolStats != null) {
                    getConsole().successf(">> Thread Pool:");
                    printSummaryStats("poolSize",
                            threadPoolStats.poolSize,
                            aggregatedThreadPoolStats.stream().mapToInt(value -> value.poolSize));
                    printSummaryStats("largestPoolSize",
                            threadPoolStats.largestPoolSize,
                            aggregatedThreadPoolStats.stream().mapToInt(value -> value.largestPoolSize));
                    printSummaryStats("activeCount",
                            threadPoolStats.activeCount,
                            aggregatedThreadPoolStats.stream().mapToInt(value -> value.activeCount));
                    printSummaryStats("taskCount",
                            threadPoolStats.taskCount,
                            aggregatedThreadPoolStats.stream().mapToLong(value -> value.taskCount));
                    printSummaryStats("completedTaskCount",
                            threadPoolStats.completedTaskCount,
                            aggregatedThreadPoolStats.stream().mapToLong(value -> value.completedTaskCount));
                }

                if (!aggregatedLoadAvg.isEmpty()) {
                    printSummaryStats("loadavg",
                            aggregatedLoadAvg.getLast(),
                            aggregatedLoadAvg.stream().mapToDouble(value -> value));
                }

                ConnectionPoolStats poolStats = aggregatedConnectionPoolStats.peekLast();
                if (poolStats != null) {
                    getConsole().successf(">> Connection Pool Stats:");
                    printSummaryStats("active",
                            poolStats.activeConnections,
                            aggregatedConnectionPoolStats.stream().mapToInt(value -> value.activeConnections));
                    printSummaryStats("idle",
                            poolStats.idleConnections,
                            aggregatedConnectionPoolStats.stream().mapToInt(value -> value.idleConnections));
                    printSummaryStats("waiting",
                            poolStats.threadsAwaitingConnection,
                            aggregatedConnectionPoolStats.stream().mapToInt(value -> value.threadsAwaitingConnection));
                    printSummaryStats("total",
                            poolStats.totalConnections,
                            aggregatedConnectionPoolStats.stream().mapToInt(value -> value.totalConnections));
                }
            } catch (Throwable e) {
                getConsole().warn(e.toString());
            }
        };

        if (repeatTime > 0) {
            ScheduledFuture<?> f = scheduledExecutorService
                    .scheduleAtFixedRate(r, 0, 5, TimeUnit.SECONDS);
            scheduledExecutorService
                    .schedule(() -> {
                        f.cancel(true);
                        getConsole().successf("<< Pool Stats ended");
                    }, repeatTime, TimeUnit.SECONDS);
        } else {
            r.run();
        }
    }

    private void printSummaryStats(String label, int current, IntStream histogram) {
        IntSummaryStatistics ss = histogram.summaryStatistics();
        if (ss.getCount() > 0) {
            getConsole().otherf(AnsiColor.BRIGHT_WHITE, "%20s:", label);
            getConsole().infof(" current %d, min %d, max %d, avg %.0f, samples %d",
                    current,
                    ss.getMin(),
                    ss.getMax(),
                    ss.getAverage(),
                    ss.getCount());
        }
    }

    private void printSummaryStats(String label, long current, LongStream histogram) {
        LongSummaryStatistics ss = histogram.summaryStatistics();
        if (ss.getCount() > 0) {
            getConsole().otherf(AnsiColor.BRIGHT_WHITE, "%20s:", label);
            getConsole().infof(" current %d, min %d, max %d, avg %.0f, samples %d",
                    current,
                    ss.getMin(),
                    ss.getMax(),
                    ss.getAverage(),
                    ss.getCount());
        }
    }

    private void printSummaryStats(String label, double current, DoubleStream histogram) {
        DoubleSummaryStatistics ss = histogram.summaryStatistics();
        if (ss.getCount() > 0) {
            getConsole().otherf(AnsiColor.BRIGHT_WHITE, "%20s:", label);
            getConsole().infof(" current %.1f, min %.1f, max %.1f, avg %.1f, samples %d",
                    current,
                    ss.getMin(),
                    ss.getMax(),
                    ss.getAverage(),
                    ss.getCount());
        }
    }

}
