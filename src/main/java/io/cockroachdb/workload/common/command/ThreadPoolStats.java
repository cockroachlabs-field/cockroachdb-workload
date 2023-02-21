package io.cockroachdb.workload.common.command;

import java.util.concurrent.ThreadPoolExecutor;

public class ThreadPoolStats {
    public static ThreadPoolStats from(ThreadPoolExecutor pool) {
        ThreadPoolStats instance = new ThreadPoolStats();
        instance.corePoolSize = pool.getCorePoolSize();
        instance.poolSize = pool.getPoolSize();
        instance.maximumPoolSize = pool.getMaximumPoolSize();
        instance.activeCount = pool.getActiveCount();
        instance.taskCount = pool.getTaskCount();
        instance.largestPoolSize = pool.getLargestPoolSize();
        instance.completedTaskCount = pool.getCompletedTaskCount();
        return instance;
    }

    public int maximumPoolSize;

    public int poolSize;

    public int activeCount;

    public long corePoolSize;

    public long taskCount;

    public int largestPoolSize;

    public long completedTaskCount;
}
