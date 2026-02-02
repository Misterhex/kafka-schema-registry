package io.schemaregistry.abtest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "abtest")
public class AbTestProperties {

    private String confluentUrl = "http://localhost:8085";
    private String mirrorUrl = "http://localhost:8086";
    private String mirrorUsername = "admin";
    private String mirrorPassword = "test";
    private String reportFile = "ab-test-report.json";

    public String getConfluentUrl() {
        return confluentUrl;
    }

    public void setConfluentUrl(String confluentUrl) {
        this.confluentUrl = confluentUrl;
    }

    public String getMirrorUrl() {
        return mirrorUrl;
    }

    public void setMirrorUrl(String mirrorUrl) {
        this.mirrorUrl = mirrorUrl;
    }

    public String getMirrorUsername() {
        return mirrorUsername;
    }

    public void setMirrorUsername(String mirrorUsername) {
        this.mirrorUsername = mirrorUsername;
    }

    public String getMirrorPassword() {
        return mirrorPassword;
    }

    public void setMirrorPassword(String mirrorPassword) {
        this.mirrorPassword = mirrorPassword;
    }

    public String getReportFile() {
        return reportFile;
    }

    public void setReportFile(String reportFile) {
        this.reportFile = reportFile;
    }
}
