package io.cockroachdb.workload.common.command;

import org.springframework.context.ApplicationEvent;

public class CommandExceptionEvent extends ApplicationEvent {
    private Throwable throwable;

    public CommandExceptionEvent(Object source, Throwable throwable) {
        super(source);
        this.throwable = throwable;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
