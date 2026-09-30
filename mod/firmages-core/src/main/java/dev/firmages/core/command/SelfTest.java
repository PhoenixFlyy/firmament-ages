package dev.firmages.core.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal self-test harness (SPEC §12, levels U and P). Pure Java: the same suites run in JUnit and through
 * {@code /firmages selftest}. The report goes to {@code logs/firmages-selftest.json}.
 */
public final class SelfTest {
    public static final String REPORT_FILE = "firmages-selftest.json";

    public record Case(String suite, String name, boolean passed, String detail) {}

    public interface Suite {
        String name();

        void run(Recorder r);
    }

    @FunctionalInterface
    public interface Body {
        /** @return detail text on success; throw {@link AssertionError} or any exception on failure. */
        String run() throws Exception;
    }

    public static final class Recorder {
        private final List<Case> cases = new ArrayList<>();
        private String suite = "";

        void suite(String name) {
            suite = name;
        }

        public void test(String name, Body body) {
            try {
                String detail = body.run();
                cases.add(new Case(suite, name, true, detail == null ? "" : detail));
            } catch (Throwable t) {
                cases.add(new Case(suite, name, false, t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
        }

        public List<Case> cases() {
            return cases;
        }
    }

    public record Report(List<Case> cases, Instant finished) {
        public long passed() {
            return cases.stream().filter(Case::passed).count();
        }

        public long failed() {
            return cases.size() - passed();
        }

        public String toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("finished", finished.toString());
            o.addProperty("passed", passed());
            o.addProperty("failed", failed());
            JsonArray arr = new JsonArray();
            for (Case c : cases) {
                JsonObject co = new JsonObject();
                co.addProperty("suite", c.suite());
                co.addProperty("name", c.name());
                co.addProperty("passed", c.passed());
                co.addProperty("detail", c.detail());
                arr.add(co);
            }
            o.add("cases", arr);
            return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o);
        }

        public void write(Path file) throws IOException {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, toJson(), StandardCharsets.UTF_8);
        }
    }

    private SelfTest() {}

    public static Report run(List<? extends Suite> suites) {
        Recorder r = new Recorder();
        for (Suite s : suites) {
            r.suite(s.name());
            try {
                s.run(r);
            } catch (Throwable t) {
                r.cases.add(new Case(s.name(), "<suite>", false, "suite crashed: " + t));
            }
        }
        return new Report(List.copyOf(r.cases), Instant.now());
    }

    // ---- assertion helpers
    public static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void checkEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }
}
