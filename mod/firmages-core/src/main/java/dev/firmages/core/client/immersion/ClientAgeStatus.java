package dev.firmages.core.client.immersion;

import dev.firmages.core.immersion.AgeStatus;
import net.minecraft.Util;

/** The client's copy of {@code firmages:age_status} (SPEC §18) and when the Age indicator was last triggered. */
public final class ClientAgeStatus {
    private static AgeStatus status = AgeStatus.UNKNOWN;
    private static boolean known;
    private static long shownAt = Long.MIN_VALUE;

    private ClientAgeStatus() {}

    public static AgeStatus get() {
        return status;
    }

    public static boolean known() {
        return known;
    }

    /** The hand-breaking rule holds (SPEC §19.2); false before the first status, the server decides alone until then. */
    public static boolean toolRule() {
        return known && status.toolRule();
    }

    /** Last login or Age change, for the indicator's fade. */
    public static long shownAt() {
        return shownAt;
    }

    static void receive(AgeStatus next) {
        boolean trigger = !known || next.age() != status.age();
        status = next;
        known = true;
        if (trigger) shownAt = Util.getMillis();
    }

    static void reset() {
        status = AgeStatus.UNKNOWN;
        known = false;
        shownAt = Long.MIN_VALUE;
    }
}
