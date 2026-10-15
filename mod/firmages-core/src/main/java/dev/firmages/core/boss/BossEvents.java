package dev.firmages.core.boss;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.origin.OriginEvent;
import dev.firmages.core.origin.OriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Game-bus hooks of The Primordial. The core answers its own finale hooks (SPEC §16.3): the Gathering at the altar and
 * {@code /firmages origin fight} ({@link OriginEvent.SummonBoss}) summon the boss above the altar; its final-boss tag
 * then wins the finale through {@link OriginService}. The damage it takes is scaled after armour
 * ({@link PrimordialEntity#scaleDamage}): NeoForge applies armour, enchantments and absorption first and fires
 * {@link LivingDamageEvent.Pre} with the amount that is about to come off its health.
 */
public final class BossEvents {
    private BossEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(BossEvents::onDamagePre);
        bus.addListener(BossEvents::onGathering);
        bus.addListener(BossEvents::onSummonBoss);
    }

    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof PrimordialEntity boss && !boss.level().isClientSide) {
            event.setNewDamage(boss.scaleDamage(event.getSource(), event.getNewDamage()));
        }
    }

    /** The whole team stands at the altar: The Primordial wakes, its pool scaled to the Gathering's size. */
    private static void onGathering(OriginEvent.Gathering e) {
        if (OriginService.fightActive(e.getLevel())) return; // a forced Gathering while it lives
        if (summon(e.getLevel(), e.getAltar(), e.getPlayers().size()) != null) {
            FirmagesCore.LOGGER.info("The Origin: Gathering #{} of {} player(s): The Primordial summoned", e.getCount(), e.getPlayers().size());
        } else {
            FirmagesCore.LOGGER.warn("The Origin: Gathering #{}: The Primordial could not be summoned (the altar's chunk is not loaded)", e.getCount());
        }
    }

    /** {@code /firmages origin fight}: the boss at once, sized by the players present when it wakes. */
    private static void onSummonBoss(OriginEvent.SummonBoss e) {
        if (e.answered()) return;
        if (OriginService.fightActive(e.getLevel())) {
            e.refuse("The Primordial is already awake");
        } else if (summon(e.getLevel(), e.getAltar(), 0) != null) {
            e.summoned();
        } else {
            e.refuse("the altar's chunk is not loaded; stand in The Origin first");
        }
    }

    /**
     * The Primordial above {@code altar} (it takes the altar as its home on its first tick). {@code partySize} 0 counts
     * the players near it when it wakes. Only while the altar's chunk ticks entities: a boss put into an unloaded chunk
     * would wait there unseen by {@link OriginService#fightActive}, and every further summon would add another.
     * @return the boss, or null when the chunk is not loaded
     */
    @Nullable
    public static PrimordialEntity summon(ServerLevel level, BlockPos altar, int partySize) {
        if (!level.isPositionEntityTicking(altar)) return null;
        PrimordialEntity b = BossRegistry.PRIMORDIAL.get().create(level);
        if (b == null) return null;
        b.moveTo(altar.getX() + 0.5, altar.getY() + PrimordialGeometry.HOVER, altar.getZ() + 0.5, 0, 0);
        if (partySize > 0) b.setPartySize(partySize);
        return level.addFreshEntity(b) ? b : null;
    }
}
