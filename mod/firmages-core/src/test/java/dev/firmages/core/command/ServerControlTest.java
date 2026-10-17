package dev.firmages.core.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §10.1: the confirmation window of /stop, the RESTART marker and the /restart countdown. */
class ServerControlTest {

    @Test
    void confirmWithinWindow() {
        var c = new ServerControl.Confirmations(30_000);
        c.ask("Server", 1_000);
        assertTrue(c.confirm("Server", 31_000), "30 s after /stop still counts");
    }

    @Test
    void confirmAfterWindowFails() {
        var c = new ServerControl.Confirmations(30_000);
        c.ask("Server", 1_000);
        assertFalse(c.confirm("Server", 31_001), "expired");
        c.ask("Server", 40_000);
        assertTrue(c.confirm("Server", 40_001), "a new /stop opens a new window");
    }

    @Test
    void confirmWithoutAskFails() {
        var c = new ServerControl.Confirmations(30_000);
        assertFalse(c.confirm("Server", 0));
    }

    @Test
    void confirmIsOncePerAsk() {
        var c = new ServerControl.Confirmations(30_000);
        c.ask("Rcon", 0);
        assertTrue(c.confirm("Rcon", 10));
        assertFalse(c.confirm("Rcon", 20), "a second confirm needs a second /stop");
    }

    @Test
    void confirmIsPerSource() {
        var c = new ServerControl.Confirmations(30_000);
        c.ask("Felix", 0);
        assertFalse(c.confirm("Server", 10), "the console cannot confirm a player's /stop");
        assertTrue(c.confirm("Felix", 20), "the player's ask is still pending");
    }

    @Test
    void clearForgetsAsks() {
        var c = new ServerControl.Confirmations(30_000);
        c.ask("Server", 0);
        c.clear();
        assertFalse(c.confirm("Server", 1));
    }

    @Test
    void markerSitsInTheServerDirectory() {
        assertEquals(Path.of("RESTART"), ServerControl.markerPath(Path.of("")), "MinecraftServer.getServerDirectory() is Path.of(\"\")");
        assertEquals(Path.of("srv", "RESTART"), ServerControl.markerPath(Path.of("srv")));
    }

    @Test
    void markerWriteAndDelete(@TempDir Path dir) throws Exception {
        ServerControl.writeMarker(dir);
        assertTrue(Files.isRegularFile(dir.resolve("RESTART")));
        ServerControl.writeMarker(dir); // a second /restart overwrites
        assertTrue(ServerControl.deleteMarker(dir));
        assertFalse(Files.exists(dir.resolve("RESTART")));
        assertFalse(ServerControl.deleteMarker(dir), "nothing to delete");
    }

    @Test
    void countdownAnnouncesTenThenFiveToOne() {
        var c = new ServerControl.Countdown(0, 10);
        List<Integer> said = new ArrayList<>();
        for (long t = 0; t < 10_000; t += 50) c.announce(t).ifPresent(said::add);
        assertEquals(List.of(10, 5, 4, 3, 2, 1), said);
        assertFalse(c.finished(9_999));
        assertTrue(c.finished(10_000));
    }

    @Test
    void countdownSkipsToTheLatestAfterLag() {
        var c = new ServerControl.Countdown(0, 10);
        assertEquals(OptionalInt.of(10), c.announce(0));
        assertEquals(OptionalInt.of(3), c.announce(7_200), "5, 4 and 3 were due: only 3 is said");
        assertEquals(OptionalInt.empty(), c.announce(7_300));
        assertEquals(OptionalInt.of(2), c.announce(8_000));
    }

    @Test
    void shortCountdown() {
        var c = new ServerControl.Countdown(0, 3);
        List<Integer> said = new ArrayList<>();
        for (long t = 0; t <= 3_000; t += 100) c.announce(t).ifPresent(said::add);
        assertEquals(List.of(3, 2, 1), said);
    }
}
