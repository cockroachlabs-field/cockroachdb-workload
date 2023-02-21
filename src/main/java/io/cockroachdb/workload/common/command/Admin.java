package io.cockroachdb.workload.common.command;

import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.commands.Quit;

import io.cockroachdb.workload.common.util.RandomData;
import jakarta.annotation.PostConstruct;

@ShellComponent
@ShellCommandGroup("Admin Commands")
public class Admin extends AbstractCommand implements Quit.Command {
    @PostConstruct
    public void init() {
        RandomData.randomCurrency();
    }

    @ShellMethod(value = "Cancel active workloads", key = {"cancel", "x"})
    public void cancel() {
        getExecutorTemplate().cancelFutures();
    }
}
