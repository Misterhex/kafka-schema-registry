package io.schemaregistry.abtest;

import io.schemaregistry.abtest.model.TestReport;
import io.schemaregistry.abtest.report.ReportGenerator;
import io.schemaregistry.abtest.runner.AbTestRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AbTestApplication implements CommandLineRunner {

    private final AbTestRunner runner;
    private final ReportGenerator reportGenerator;

    public AbTestApplication(AbTestRunner runner, ReportGenerator reportGenerator) {
        this.runner = runner;
        this.reportGenerator = reportGenerator;
    }

    public static void main(String[] args) {
        SpringApplication.run(AbTestApplication.class, args);
    }

    @Override
    public void run(String... args) {
        TestReport report = runner.execute();
        reportGenerator.generate(report);

        int exitCode = report.summary().diff() > 0 ? 1 : 0;
        System.exit(exitCode);
    }
}
