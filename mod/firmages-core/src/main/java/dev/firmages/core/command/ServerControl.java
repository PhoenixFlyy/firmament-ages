package dev.firmages.core.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The rules behind {@code /stop} and {@code /restart} (SPEC §10.1): the confirmation window, the {@code RESTART} marker
 * for the start scripts and the restart countdown. No Minecraft types, so JUnit tests them ({@code ServerControlTest}).
 */
public final class ServerControl {
    /** File in the server directory: present after the JVM ended = the start script deletes it and starts again. */
    public static final String RESTART_MARKER = "RESTART";
    /** {@code /stop confirm} counts within this many milliseconds after {@code /stop} of the same source. */
    public static final long CONFIRM_WINDOW_MS = 30_000L;
    /** {@code /restart} halts the server this many seconds after the command. */
    public static final int RESTART_SECONDS = 10;

    private ServerControl() {}

    public static Path markerPath(Path serverDirectory) {
        return serverDirectory.resolve(RESTART_MARKER);
    }

    public static void writeMarker(Path serverDirectory) throws IOException {
        Files.writeString(markerPath(serverDirectory),
            "Written by /restart. The start script deletes this file and starts the server again.\n", StandardCharsets.UTF_8);
    }

    /** True if a marker was there. */
    public static boolean deleteMarker(Path serverDirectory) throws IOException {
        return Files.deleteIfExists(markerPath(serverDirectory));
    }

    /** One pending {@code /stop} per command source (the player's name, "Server" for the console, "Rcon"). */
    public static final class Confirmations {
        private final long windowMs;
        private final Map<String, Long> asked = new HashMap<>();

        public Confirmations(long windowMs) {
            this.windowMs = windowMs;
        }

        public void ask(String source, long nowMs) {
            asked.put(source, nowMs);
        }

        /** True once per ask and only within the window; a missing or expired ask is false (and forgotten). */
        public boolean confirm(String source, long nowMs) {
            Long at = asked.remove(source);
            return at != null && nowMs >= at && nowMs - at <= windowMs;
        }

        public void clear() {
            asked.clear();
        }
    }

    /**
     * Announcements at {@code seconds} left, then at 5, 4, 3, 2 and 1 second left; the server halts at 0. After a lag
     * spike only the latest due announcement is returned, so players never get several lines at once.
     */
    public static final class Countdown {
        private final long startMs;
        private final int seconds;
        private final int[] schedule;
        private int next;

        public Countdown(long startMs, int seconds) {
            if (seconds < 1) throw new IllegalArgumentException("seconds < 1");
            this.startMs = startMs;
            this.seconds = seconds;
            int tail = Math.min(5, seconds - 1);
            this.schedule = new int[1 + tail];
            schedule[0] = seconds;
            for (int i = 0; i < tail; i++) schedule[1 + i] = tail - i;
        }

        /** Seconds left to announce now, or empty. */
        public OptionalInt announce(long nowMs) {
            int due = -1;
            while (next < schedule.length && nowMs >= startMs + (seconds - schedule[next]) * 1000L) due = schedule[next++];
            return due < 0 ? OptionalInt.empty() : OptionalInt.of(due);
        }

        public boolean finished(long nowMs) {
            return nowMs >= startMs + seconds * 1000L;
        }
    }
}
