package io.cockroachdb.workload.common.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import io.cockroachdb.workload.common.util.CockroachFacts;

@ShellComponent
@ShellCommandGroup(value = "Built-In Commands")
public class Quit extends AbstractCommand implements org.springframework.shell.standard.commands.Quit.Command {
    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @ShellMethod(value = "Exit the shell", key = {"quit", "exit", "q"})
    public void quit() {
        getConsole().successf("Did you know? %s", CockroachFacts.nextFact());
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }
}
