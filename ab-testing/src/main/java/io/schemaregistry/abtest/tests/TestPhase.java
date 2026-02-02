package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.model.TestResult;

import java.util.List;

public interface TestPhase {
    String name();
    List<TestResult> execute(int startNumber);
}
