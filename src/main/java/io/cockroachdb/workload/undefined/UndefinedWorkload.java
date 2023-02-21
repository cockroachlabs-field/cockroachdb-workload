package io.cockroachdb.workload.undefined;

import java.util.Collections;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.command.AbstractCommand;
import io.cockroachdb.workload.common.command.Workload;

@Profiles.Undefined
@ShellComponent
@ShellCommandGroup("Workload")
public class UndefinedWorkload extends AbstractCommand implements Workload {
    @Override
    public String prompt() {
        return "undefined:$ ";
    }
}
