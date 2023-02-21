package io.cockroachdb.workload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Profile;

public abstract class Profiles {
    public static List<String> all() {
        return Arrays.asList("undefined", "ledger", "outbox", "orders");
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Profile("undefined")
    public @interface Undefined {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Profile("ledger")
    public @interface Ledger {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Profile("outbox")
    public @interface Outbox {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Profile("order")
    public @interface Order {
    }
}
