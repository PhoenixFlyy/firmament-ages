package dev.firmages.core.sky;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Loot condition {@code firmages:starfall_hot} (SPEC §25.4): true when the broken block lies within
 * {@link StarfallRules#HOT_RADIUS} of a site's core and the game time is before that site's {@code coolsAt}. One map
 * lookup in the overworld's {@link SkySavedData} chunk index, no world scan, so drills and excavators pay little.
 */
public final class StarfallHotCondition implements LootItemCondition {
    public static final StarfallHotCondition INSTANCE = new StarfallHotCondition();
    public static final MapCodec<StarfallHotCondition> CODEC = MapCodec.unit(INSTANCE);

    private StarfallHotCondition() {}

    @Override
    public LootItemConditionType getType() {
        return SkyRegistry.STARFALL_HOT.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ORIGIN);
    }

    @Override
    public boolean test(LootContext ctx) {
        Vec3 origin = ctx.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null || ctx.getLevel().dimension() != Level.OVERWORLD) return false;
        BlockPos pos = BlockPos.containing(origin);
        long now = ctx.getLevel().getGameTime();
        return SkySavedData.get(ctx.getLevel().getServer()).siteNear(pos).map(s -> !StarfallRules.cold(now, s.coolsAt)).orElse(false);
    }
}
