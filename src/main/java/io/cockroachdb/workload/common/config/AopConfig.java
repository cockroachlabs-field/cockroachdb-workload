package io.cockroachdb.workload.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import io.cockroachdb.workload.common.aspect.RetryableAspect;
import io.cockroachdb.workload.common.aspect.SessionHintsAspect;

@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfig {
    @Bean
    public RetryableAspect retryableAspect() {
        return new RetryableAspect();
    }

    @Bean
    public SessionHintsAspect sessionHintsAspect() {
        return new SessionHintsAspect();
    }
}
