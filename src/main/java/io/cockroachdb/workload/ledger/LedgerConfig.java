package io.cockroachdb.workload.ledger;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.config.AopConfig;
import io.cockroachdb.workload.common.config.PersistenceConfig;

@Configuration
@Import({PersistenceConfig.class, AopConfig.class})
@EnableJpaRepositories(basePackages = {"io.cockroachdb.workload.ledger"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Profiles.Ledger
public class LedgerConfig {
}
