package io.cockroachdb.workload.outbox;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.command.AbstractCommand;
import io.cockroachdb.workload.common.command.Workload;
import io.cockroachdb.workload.common.util.DurationFormat;
import io.cockroachdb.workload.common.util.Multiplier;

@Profiles.Outbox
@ShellComponent
@ShellCommandGroup("Workload")
public class OutboxWorkload extends AbstractCommand implements Workload {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public String prompt() {
        return "outbox:$ ";
    }

    @ShellMethod(value = "Initialize outbox workload")
    public void init(
            @ShellOption(help = "number of partitions (tables)", defaultValue = "1") int partitions,
            @ShellOption(help = "drop and create schema", defaultValue = "false") boolean drop) {
        SchemaSupport schemaSupport = new SchemaSupport(getDataSource());
        if (drop) {
            getConsole().successf("Dropping %d tables\n", partitions);
            schemaSupport.dropSchema(partitions);
        }
        getConsole().successf("Creating %d tables\n", partitions);
        schemaSupport.createSchema(partitions);
        onPostInit();
    }

    @ShellMethodAvailability("initCheck")
    @ShellMethod(value = "Run outbox workload")
    public void run(
            @ShellOption(help = "number of threads per partition", defaultValue = "-1") int threads,
            @ShellOption(help = "number of partitions (tables)", defaultValue = "1") int partitions,
            @ShellOption(help = "execution duration (expression)", defaultValue = "30m") String duration,
            @ShellOption(help = "batch size", defaultValue = "64") String batchSize,
            @ShellOption(help = "dry run", defaultValue = "false") boolean dryRun,
            @ShellOption(help = "JSON payload size (1k|5k|10k|15k|100k)", defaultValue = "1k") String payload
    ) {
        final String payloadPath = "db/outbox/payload-" + payload + ".json";

        if (!new ClassPathResource(payloadPath).exists()) {
            getConsole().errorf("Invalid payload (not found): %s", payloadPath);
            return;
        }

        final int numThreads = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();

        int batchSizeNum = Multiplier.parseInt(batchSize);
        Duration runtimeDuration = DurationFormat.parseDuration(duration);

        getConsole().success(">> Starting outbox workload <<\n");
        getConsole().infof("Number of threads: %d\n", numThreads);
        getConsole().infof("Number of partitions: %d\n", partitions);
        getConsole().infof("Runtime duration: %s\n", duration);
        getConsole().infof("Batch size: %d\n", batchSizeNum);
        getConsole().infof("Payload file: %s\n", payloadPath);

        IntStream.rangeClosed(1, partitions).forEach(p -> {
            IntStream.rangeClosed(1, numThreads).forEach(t -> {
                if (!dryRun) {
                    getExecutorTemplate().submit(
                            "partition #" + p + " thread " + t + " (" + payload + ")",
                            () -> submitBatch(p, batchSizeNum, payloadPath),
                            runtimeDuration);
                }
            });
        });
    }

    private void submitBatch(int partition, int batchSize, String payloadPath) {
        jdbcTemplate.batchUpdate(String.format("INSERT INTO outbox_%d (aggregate_type,aggregate_id,event_type,payload) "
                        + "VALUES (?,?,?,?)", partition),
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        OutboxEvent outboxEvent = newOutboxEvent(payloadPath);
                        ps.setString(1, outboxEvent.getAggregateType());
                        ps.setString(2, outboxEvent.getAggregateId());
                        ps.setString(3, outboxEvent.getEventType());
                        try {
                            ps.setCharacterStream(4, new EncodedResource(
                                    new ClassPathResource(outboxEvent.getPayload())).getReader());
                        } catch (IOException e) {
                            throw new SQLException(e);
                        }
                    }

                    @Override
                    public int getBatchSize() {
                        return batchSize;
                    }
                });
    }

    private OutboxEvent newOutboxEvent(String payloadPath) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(UUID.randomUUID().toString());
        outboxEvent.setEventType("random_event");
        outboxEvent.setAggregateType("random");
        outboxEvent.setPayload(payloadPath);
        return outboxEvent;
    }
}
