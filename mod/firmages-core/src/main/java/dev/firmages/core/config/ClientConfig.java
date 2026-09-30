package dev.firmages.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code firmages-client.toml} (SPEC §9). Read by the ceremony client code (M4 and later). */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SKY_TINT;
    public static final ModConfigSpec.BooleanValue STOP_MUSIC;
    public static final ModConfigSpec.DoubleValue PARTICLE_SCALE;
    public static final ModConfigSpec.BooleanValue AMBIENT_DRONE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Age ceremony effects").push("effects");
        SKY_TINT = b.define("skyTint", true);
        STOP_MUSIC = b.define("stopMusic", true);
        PARTICLE_SCALE = b.defineInRange("particleScale", 1.0, 0.0, 4.0);
        AMBIENT_DRONE = b.define("ambientDrone", true);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}
}
