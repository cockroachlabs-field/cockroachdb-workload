package io.cockroachdb.workload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.shell.boot.ApplicationRunnerAutoConfiguration;
import org.springframework.shell.jline.InteractiveShellRunner;
import org.springframework.util.StringUtils;

@Configuration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        ApplicationRunnerAutoConfiguration.class
})
@EnableConfigurationProperties
@ComponentScan(basePackages = "io.cockroachdb")
@Order(InteractiveShellRunner.PRECEDENCE - 100)
public class Application implements ApplicationRunner {
    private static void printHelpAndExit(String message) {
        System.out.println("Usage: workload.jar <options> [<profile> ..]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("--help          this help");
        System.out.println();
        System.out.println("Profiles:");
        Profiles.all().forEach(profile -> System.out.printf("%s\n", profile));
        System.out.println();
        System.out.println(message);
        System.exit(0);
    }

    public static void main(String[] args) {
        List<String> profiles = new ArrayList<>();
        List<String> argsFinal = new ArrayList<>();

        Arrays.asList(args).forEach(s -> {
            if (s.equals("--help")) {
                printHelpAndExit("");
            }
            if (Profiles.all().contains(s)) {
                profiles.add(s);
            } else {
                argsFinal.add(s);
            }
        });

        if (!profiles.isEmpty()) {
            System.setProperty("spring.profiles.active", StringUtils.collectionToCommaDelimitedString(profiles));
        }

        new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .headless(true)
                .profiles(profiles.toArray(new String[] {}))
                .logStartupInfo(true)
                .run(argsFinal.toArray(new String[] {}));
    }

    @Autowired
    @Lazy
    private InteractiveShellRunner shellRunner;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        shellRunner.run(args);
    }
}
