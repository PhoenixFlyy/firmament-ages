package dev.firmages.core.command;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the pure {@link CoreSuite} (the same cases as {@code /firmages selftest core}) as JUnit tests. */
class CoreSuiteTest {

    @TestFactory
    Stream<DynamicTest> coreSuite() {
        SelfTest.Report report = SelfTest.run(List.of(new CoreSuite()));
        return report.cases().stream().map(c -> DynamicTest.dynamicTest(c.name(),
            () -> assertTrue(c.passed(), c.name() + ": " + c.detail())));
    }

    @Test
    void reportCountsAndJson() {
        SelfTest.Suite suite = new SelfTest.Suite() {
            public String name() { return "demo"; }
            public void run(SelfTest.Recorder r) {
                r.test("ok", () -> "fine");
                r.test("bad", () -> { throw new AssertionError("nope"); });
            }
        };
        SelfTest.Report report = SelfTest.run(List.of(suite));
        assertEquals(1, report.passed());
        assertEquals(1, report.failed());
        String json = report.toJson();
        assertTrue(json.contains("\"failed\": 1"), json);
        assertTrue(json.contains("AssertionError: nope"), json);
        assertFalse(report.cases().get(1).passed());
    }
}
