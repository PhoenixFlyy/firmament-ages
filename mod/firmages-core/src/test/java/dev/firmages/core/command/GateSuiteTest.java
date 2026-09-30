package dev.firmages.core.command;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the pure {@link GateSuite} (the same cases as {@code /firmages selftest gate}) as JUnit tests. */
class GateSuiteTest {

    @TestFactory
    Stream<DynamicTest> gateSuite() {
        SelfTest.Report report = SelfTest.run(List.of(new GateSuite()));
        return report.cases().stream().map(c -> DynamicTest.dynamicTest(c.name(),
            () -> assertTrue(c.passed(), c.name() + ": " + c.detail())));
    }
}
