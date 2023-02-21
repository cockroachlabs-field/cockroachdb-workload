package io.cockroachdb.workload.common.command;

import org.springframework.context.ApplicationEvent;

public class InitializedEvent extends ApplicationEvent {
    public InitializedEvent(Object source) {
        super(source);
    }
}
