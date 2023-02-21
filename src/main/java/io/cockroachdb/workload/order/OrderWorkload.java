package io.cockroachdb.workload.order;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.DatabasePopulator;
import io.cockroachdb.workload.common.command.AbstractCommand;
import io.cockroachdb.workload.common.command.Workload;
import io.cockroachdb.workload.common.util.DurationFormat;
import io.cockroachdb.workload.common.util.Multiplier;
import io.cockroachdb.workload.common.util.RandomData;
import io.cockroachdb.workload.order.model.Order;
import io.cockroachdb.workload.order.model.OrderEntities;
import io.cockroachdb.workload.order.repository.OrderRepository;

@Profiles.Order
@ShellComponent
@ShellCommandGroup("Workload")
public class OrderWorkload extends AbstractCommand implements Workload {
    @Autowired
    @Qualifier("jdbcOrderRepository")
    private OrderRepository jdbcOrderRepository;

    @Autowired
    @Qualifier("jpaOrderRepository")
    private OrderRepository jpaOrderRepository;

    @Override
    public String prompt() {
        return "order:$ ";
    }

    @Override
    protected List<Resource> sqlFiles() {
        return Arrays.asList(
                new ClassPathResource("db/order/create-order.sql"),
                new ClassPathResource("db/order/drop-order.sql")
        );
    }

    @ShellMethod(value = "Initialize order workload")
    public void init(
            @ShellOption(help = "drop tables before creating", defaultValue = "false") boolean drop) {
        if (drop) {
            DatabasePopulator.executeScripts(getDataSource(), "db/order/drop-order.sql");
        }
        DatabasePopulator.executeScripts(getDataSource(), "db/order/create-order.sql");
        onPostInit();
    }

    @ShellMethod(value = "Run order readers and writers")
    public void run(
            @ShellOption(help = "number of read threads", defaultValue = "-1") int readThreads,
            @ShellOption(help = "number of write threads", defaultValue = "-1") int writeThreads,
            @ShellOption(help = "write batch size", defaultValue = "16") String batchSize,
            @ShellOption(help = "execution duration", defaultValue = "45m") String duration,
            @ShellOption(help = "data access method (jdbc|jpa)", defaultValue = "jdbc") String method,
            @ShellOption(help = "include JSON payload (customer profile)", defaultValue = "false") boolean includeJson,
            @ShellOption(help = "follower reads", defaultValue = "false") boolean followerReads
    ) {
        if (writeThreads <= 0) {
            writeThreads = Runtime.getRuntime().availableProcessors();
        }
        if (writeThreads > 0) {
            runWriters(writeThreads, batchSize, DurationFormat.parseDuration(duration), method, includeJson);
        }

        if (readThreads <= 0) {
            readThreads = Runtime.getRuntime().availableProcessors();
        }
        if (readThreads > 0) {
            runReaders(readThreads, 1000, DurationFormat.parseDuration(duration), method, followerReads);
        }
    }

    private void runWriters(
            int writeThreads,
            String batchSize,
            Duration duration,
            String method,
            boolean includeJson
    ) {
        final int batchSizeNum = Multiplier.parseInt(batchSize);
        final OrderRepository orderRepository = getOrderRepositoryUsing(method);

        getConsole().successf(">> Starting order writers\n");
        getConsole().infof("Number of write threads: %d", writeThreads);
        getConsole().infof("Batch size: %d", batchSizeNum);
        getConsole().infof("Include JSON payload: %s", includeJson);
        getConsole().infof("Runtime duration: %s", duration);
        getConsole().infof("Data access method: %s", method);

        // Consumers
        IntStream.rangeClosed(1, writeThreads).forEach(value -> {
            getExecutorTemplate().submit("order writer #" + value + " (batch size " + batchSize + ")",
                    () -> {
                        List<Order> orderBatch = OrderEntities.generateOrderEntities(batchSizeNum);
                        orderRepository.insertOrders(orderBatch, includeJson);
                    }, duration);
        });
    }

    private void runReaders(
            int readThreads,
            int limit,
            Duration duration,
            String method,
            boolean followerReads
    ) {
        final OrderRepository orderRepository = getOrderRepositoryUsing(method);

        List<UUID> ids = new ArrayList<>();

        OrderRepository repository = getOrderRepositoryUsing(method);
        Optional<UUID> nextId = repository.findLowestId();
        if (nextId.isEmpty()) {
            getConsole().warn("No orders");
            return;
        }

        List<Order> orders
                = orderRepository.findOrders(nextId.get(), limit);
        if (orders.isEmpty()) {
            getConsole().warn("No orders");
            return;
        }

        orders.forEach(order -> ids.add(order.getId()));

        getConsole().successf(">> Starting order readers\n");
        getConsole().infof("Number of read threads: %d", readThreads);
        getConsole().infof("Runtime duration: %s", duration);
        getConsole().infof("Data access method: %s", method);
        getConsole().infof("Follower reads: %s", followerReads);

        IntStream.rangeClosed(1, readThreads).forEach(value -> {
            getConsole().successf("Starting read thread #%d across %,d key tuples", value, ids.size());
            getExecutorTemplate().submit("order reader #" + value, () -> {
                UUID id = RandomData.selectRandom(ids);
                orderRepository.readOrder(id, followerReads);
            }, duration);
        });
    }

    @ShellMethod(value = "List orders using pagination")
    public void list(@ShellOption(help = "data access method (jdbc|jpa)", defaultValue = "jdbc") String method,
                     @ShellOption(help = "page size", defaultValue = "64") int limit,
                     @ShellOption(help = "max pages", defaultValue = "-1") int pageLimit
    ) {
        OrderRepository repository = getOrderRepositoryUsing(method);
        Optional<UUID> nextId = repository.findLowestId();
        if (nextId.isEmpty()) {
            getConsole().warn("No orders");
            return;
        }

        getConsole().successf(">> Listing orders using seek method (page size: %d)", pageLimit);

        pageLimit = pageLimit < 0 ? Integer.MAX_VALUE : pageLimit;

        int page = 1;
        int total = 0;
        List<Order> orders;
        UUID next = nextId.get();
        while (page < pageLimit) {
            orders = repository.findOrders(next, limit);
            getConsole().infof("Page %,d with %,d items at key %s with limit %d", page, orders.size(), next, limit);
            if (orders.isEmpty()) {
                break;
            }
            next = lastItemOf(orders).getId();
            page++;
            total += orders.size();

            orders.forEach(o -> getConsole().successf("%s", o));
        }
        ;

        getConsole().infof("%,d orders in %,d pages", total, page);
    }

    private static <T> T lastItemOf(List<T> list) {
        if (list != null && !list.isEmpty()) {
            return list.get(list.size() - 1);
        }
        return null;
    }

    private OrderRepository getOrderRepositoryUsing(String method) {
        if ("jdbc".equalsIgnoreCase(method)) {
            return jdbcOrderRepository;
        } else if ("jpa".equalsIgnoreCase(method)) {
            return jpaOrderRepository;
        }
        throw new IllegalArgumentException("Unknown access method (jdbc|jpa): " + method);
    }
}
