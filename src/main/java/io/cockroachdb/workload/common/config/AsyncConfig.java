package io.cockroachdb.workload.common.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import io.cockroachdb.workload.common.CallMetrics;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Value("${cockroachdb.workload.core-pool-size}")
    private int corePoolSize;

    private int corePoolSize() {
        return corePoolSize > 0 ? corePoolSize : Runtime.getRuntime().availableProcessors() * 4;
    }

    @Override
    @Bean(name = "jobExecutor")
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize());
        executor.setMaxPoolSize(300);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setThreadNamePrefix("worker");
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService scheduledExecutor() {
        return Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @Bean
    public CallMetrics callMetrics() {
        return new CallMetrics();
    }
}
