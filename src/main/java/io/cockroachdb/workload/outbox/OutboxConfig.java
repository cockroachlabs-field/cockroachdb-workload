package io.cockroachdb.workload.outbox;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.config.PersistenceConfig;

@Configuration
@Import(PersistenceConfig.class)
@Profiles.Outbox
public class OutboxConfig {
}
