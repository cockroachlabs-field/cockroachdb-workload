package io.cockroachdb.workload.common.command;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.stereotype.Component;

@Component
public class ShellPromptProvider implements PromptProvider {
    private enum WorkloadState {
        READY {
            @Override
            AttributedStyle foregroundColor() {
                return AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
            }
        },
        INIT {
            @Override
            AttributedStyle foregroundColor() {
                return AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA);
            }
        };

        abstract AttributedStyle foregroundColor();
    }

    private WorkloadState state = WorkloadState.INIT;

    @Autowired
    @Lazy
    private Workload workload;

    @EventListener
    public void handle(InitializedEvent event) {
        this.state = WorkloadState.READY;
    }

    @EventListener
    public void handle(CommandExceptionEvent event) {
    }

    @Override
    public AttributedString getPrompt() {
        return new AttributedString(workload.prompt(), state.foregroundColor());
    }
}
