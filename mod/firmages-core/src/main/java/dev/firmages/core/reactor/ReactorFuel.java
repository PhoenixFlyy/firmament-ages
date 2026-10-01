package dev.firmages.core.reactor;

/**
 * Fuel arithmetic of the Draconic reactor, copied from DE 3.1.4 {@code ReactorMenu} [verified, javap]: an awakened
 * draconium block, ingot and nugget add 1296, 144 and 16 to {@code reactableFuel}; large, medium and small chaos
 * fragments stand for 1296, 144 and 16 of {@code convertedFuel}; fuel plus chaos may reach 10368 + 15. Pure Java.
 */
public final class ReactorFuel {
    public static final int BLOCK = 1296;
    public static final int INGOT = 144;
    public static final int NUGGET = 16;
    /** DE's GUI cap: {@code 10383 - (int) (reactableFuel + convertedFuel)} is the room left. */
    public static final int CAP = 10368 + 15;

    public static final String FUEL_BLOCK = "draconicevolution:awakened_draconium_block";
    public static final String FUEL_INGOT = "draconicevolution:awakened_draconium_ingot";
    public static final String FUEL_NUGGET = "draconicevolution:awakened_draconium_nugget";
    public static final String CHAOS_LARGE = "draconicevolution:large_chaos_frag";
    public static final String CHAOS_MEDIUM = "draconicevolution:medium_chaos_frag";
    public static final String CHAOS_SMALL = "draconicevolution:small_chaos_frag";

    /** Chaos item ids, largest first (the order extraction uses). */
    public static final String[] CHAOS_ITEMS = {CHAOS_LARGE, CHAOS_MEDIUM, CHAOS_SMALL};
    /** Fuel item ids, largest first. */
    public static final String[] FUEL_ITEMS = {FUEL_BLOCK, FUEL_INGOT, FUEL_NUGGET};
    public static final int[] UNITS = {BLOCK, INGOT, NUGGET};

    private ReactorFuel() {}

    /** Fuel per item, 0 for anything that is not awakened draconium. */
    public static int fuelValue(String itemId) {
        return switch (itemId) {
            case FUEL_BLOCK -> BLOCK;
            case FUEL_INGOT -> INGOT;
            case FUEL_NUGGET -> NUGGET;
            default -> 0;
        };
    }

    /** Chaos per fragment, 0 for anything else. */
    public static int chaosValue(String itemId) {
        return switch (itemId) {
            case CHAOS_LARGE -> BLOCK;
            case CHAOS_MEDIUM -> INGOT;
            case CHAOS_SMALL -> NUGGET;
            default -> 0;
        };
    }

    /** Units DE still accepts (same truncation as its GUI). */
    public static int room(double fuel, double chaos) {
        return Math.max(0, CAP - (int) (fuel + chaos));
    }

    /** How many of {@code count} items worth {@code unit} each fit. */
    public static int fits(int unit, int count, double fuel, double chaos) {
        if (unit <= 0 || count <= 0) return 0;
        return Math.min(count, room(fuel, chaos) / unit);
    }

    /** True when not even one nugget fits any more. */
    public static boolean full(double fuel, double chaos) {
        return room(fuel, chaos) < NUGGET;
    }

    /**
     * Fragments that {@code chaos} can be taken out as, largest first, exactly as DE's GUI slots show them:
     * {large, medium, small}. A rest below 16 cannot leave the reactor.
     */
    public static int[] chaosSplit(double chaos) {
        int c = Math.max(0, (int) Math.floor(chaos));
        return new int[] {c / BLOCK, c % BLOCK / INGOT, c % BLOCK % INGOT / NUGGET};
    }

    /** Converted share 0..1 ({@code convertedFuel / (reactableFuel + convertedFuel)}), 0 for an empty reactor. */
    public static double conversion(double fuel, double chaos) {
        double total = fuel + chaos;
        return total <= 0 ? 0 : chaos / total;
    }

    /** Comparator output 0..15: the unconverted share of what is in the reactor. */
    public static int comparator(double fuel, double chaos) {
        double total = fuel + chaos;
        if (total <= 0 || fuel <= 0) return 0;
        return Math.max(1, (int) Math.round(15 * fuel / total));
    }
}
