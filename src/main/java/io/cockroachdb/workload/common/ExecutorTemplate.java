package io.cockroachdb.workload.common;

import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
public class ExecutorTemplate {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, AtomicInteger> workers = new ConcurrentHashMap<>();

    private final LinkedList<Future<Void>> futures = new LinkedList<>();

    private volatile boolean cancelPlease;

    @Qualifier("jobExecutor")
    @Autowired
    private ThreadPoolTaskExecutor threadPoolExecutor;

    @Autowired
    private CallMetrics callMetrics;

    public ThreadPoolExecutor getThreadPoolExecutor() {
        return threadPoolExecutor.getThreadPoolExecutor();
    }

    public boolean hasActiveWorkers() {
        return threadPoolExecutor.getActiveCount() > 0;
    }

    public Future<Void> submit(String id, Runnable runnable, Duration duration) {
        logger.info("Started '{}' to run for {}", id, duration);

        Future<Void> future = threadPoolExecutor.submit(() -> {
            final long startTime = System.currentTimeMillis();

            AtomicInteger activeWorkers = workers.computeIfAbsent(id, i -> new AtomicInteger());
            activeWorkers.incrementAndGet();

            CallMetrics.Context context = callMetrics.of(id, activeWorkers::get);

            int fails = 0;
            while (System.currentTimeMillis() - startTime < duration.toMillis()) {
                if (Thread.interrupted() || cancelPlease) {
                    logger.warn("Cancel requested");
                    break;
                }

                final long callTime = context.before();
                try {
                    runnable.run();
                    context.after(callTime, null);
                } catch (DataAccessException e) {
                    context.after(callTime, e);

                    Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
                    if (cause instanceof SQLException && "40001".equals(((SQLException) cause).getSQLState())) {
                        logger.warn("Transient SQL error - backing off", e);
                        backoff(++fails);
                    } else {
                        logger.warn("Non-transient SQL error - cancelling", e);
                        break;
                    }
                } catch (Exception e) {
                    context.after(callTime, e);
                    logger.error("Uncategorized error - cancelling", e);
                    break;
                }
            }

            activeWorkers.decrementAndGet();

            logger.info("Finished '{}'", id);

            return null;
        });
        futures.add(future);
        return future;
    }

    public Future<Void> submit(String id, Runnable runnable, int iterations) {
        logger.info("Started '{}' to run {} times", id, iterations);

        Future<Void> future = threadPoolExecutor.submitCompletable(() -> {
            AtomicInteger activeWorkers = workers.computeIfAbsent(id, i -> new AtomicInteger());
            activeWorkers.incrementAndGet();

            CallMetrics.Context context = callMetrics.of(id, activeWorkers::get);

            loop:
            for (int i = 0; i < iterations; i++) {
                if (cancelPlease) {
                    logger.warn("Cancel requested for {}", id);
                    break;
                }

                for (int fails = 0; fails < 10; fails++) {
                    final long callTime = context.before();
                    try {
                        runnable.run();
                        context.after(callTime, null);
                    } catch (DataAccessException e) {
                        context.after(callTime, e);

                        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
                        if (cause instanceof SQLException && "40001".equals(((SQLException) cause).getSQLState())) {
                            logger.warn("Transient SQL error - backing off", e);
                            backoff(++fails);
                        } else {
                            logger.warn("Non-transient SQL error - cancelling", e);
                            break loop;
                        }
                    } catch (Exception e) {
                        context.after(callTime, e);
                        logger.error("Uncategorized error - cancelling", e);
                        break loop;
                    }
                }
            }

            activeWorkers.decrementAndGet();

            logger.info("Finished '{}'", id);

            return null;
        });
        futures.add(future);
        return future;
    }

    private void backoff(int fails) {
        try {
            long backoffMillis = Math.min((long) (Math.pow(2, ++fails) + Math.random() * 1000), 5000);
            Thread.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void shutdown() {
        this.cancelPlease = true;
        cancelFutures();
    }

    public void cancelFutures() {
        this.cancelPlease = true;

        while (!futures.isEmpty()) {
            logger.debug("Cancelling {} futures", futures.size());
            Future<Void> future = futures.poll();
            try {
                future.cancel(true);
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                logger.error("Failed: " + e.toString());
            } catch (CancellationException e) {
                // ok
            }
        }

        logger.debug("All futures cancelled");
        cancelPlease = false;
    }
}
