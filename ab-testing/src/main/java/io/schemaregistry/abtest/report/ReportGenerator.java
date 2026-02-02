package io.schemaregistry.abtest.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.schemaregistry.abtest.config.AbTestProperties;
import io.schemaregistry.abtest.model.TestReport;
import io.schemaregistry.abtest.model.TestResult;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class ReportGenerator {

    private final AbTestProperties properties;
    private final ObjectMapper mapper;

    public ReportGenerator(AbTestProperties properties) {
        this.properties = properties;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void generate(TestReport report) {
        printConsoleReport(report);
        writeJsonReport(report);
    }

    private void printConsoleReport(TestReport report) {
        System.out.println();
        System.out.println("A/B Test: Schema Registry Mirror vs Confluent");
        System.out.println("==============================================");

        String currentPhase = "";
        for (TestResult result : report.results()) {
            if (!result.phase().equals(currentPhase)) {
                currentPhase = result.phase();
                System.out.println();
                System.out.println(currentPhase);
            }

            String statusLabel = switch (result.status()) {
                case MATCH -> "\033[32m MATCH \033[0m";
                case DIFF -> "\033[31m  DIFF \033[0m";
                case STRUCTURAL -> "\033[33mSTRUCT \033[0m";
            };

            String statusCodes = "[%d=%d]".formatted(result.confluentStatus(), result.mirrorStatus());
            String detail = result.diffDetail() != null ? " " + result.diffDetail() : "";

            System.out.printf("  %s %2d. %-40s %s%s%n",
                    statusLabel, result.number(), result.name(), statusCodes, detail);
        }

        System.out.println();
        System.out.println("==============================================");
        TestReport.Summary s = report.summary();
        System.out.printf("RESULTS: %d MATCH | %d DIFF | %d STRUCTURAL | %d TOTAL%n",
                s.match(), s.diff(), s.structural(), s.total());
        System.out.printf("Compatibility: %.1f%%%n", s.compatibilityPercent());
        System.out.println("==============================================");
        System.out.println();
    }

    private void writeJsonReport(TestReport report) {
        try {
            File outputFile = new File(properties.getReportFile());
            mapper.writeValue(outputFile, report);
            System.out.println("Report written to: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write report: " + e.getMessage());
        }
    }
}
