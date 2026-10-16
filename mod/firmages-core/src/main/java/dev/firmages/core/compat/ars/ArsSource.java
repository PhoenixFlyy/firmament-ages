package dev.firmages.core.compat.ars;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Source from Ars Nouveau's jars for the Arcane Catalyst (SPEC §41.3): Ars Nouveau 5.13.3 [verified, javap]
 * {@code SourceUtil.takeSourceWithParticles(BlockPos, Level, int range, int amount)} takes the amount from one jar in
 * range (with Ars's particle trail) and returns that jar's provider, or null when no jar holds enough. Looked up by
 * name, so the build needs no Ars jar; used only when Ars Nouveau is loaded.
 */
public final class ArsSource {
    private static final MethodHandle TAKE = find();

    private ArsSource() {}

    private static MethodHandle find() {
        try {
            Class<?> util = Class.forName("com.hollingsworth.arsnouveau.api.util.SourceUtil");
            Class<?> provider = Class.forName("com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider");
            return MethodHandles.publicLookup().findStatic(util, "takeSourceWithParticles",
                MethodType.methodType(provider, BlockPos.class, Level.class, int.class, int.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            FirmagesCore.LOGGER.warn("Ars Nouveau SourceUtil.takeSourceWithParticles not found; the Arcane Catalyst gets no Source: {}", e.toString());
            return null;
        }
    }

    public static boolean take(Level level, BlockPos pos, int range, int amount) {
        if (TAKE == null) return false;
        try {
            return TAKE.invoke(pos, level, range, amount) != null;
        } catch (Throwable t) {
            FirmagesCore.LOGGER.warn("Arcane Catalyst at {}: taking Source failed: {}", pos, t.toString());
            return false;
        }
    }
}
