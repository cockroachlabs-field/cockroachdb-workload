package io.cockroachdb.workload.order;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.config.AopConfig;
import io.cockroachdb.workload.common.config.PersistenceConfig;

@Configuration
@Import({PersistenceConfig.class, AopConfig.class})
@EnableJpaRepositories(basePackages = {"io.cockroachdb.workload.order"})
@Profiles.Order
public class OrderConfig {
}
