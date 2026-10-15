package dev.firmages.core.boss;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Game-bus hooks of The Primordial. The damage it takes is scaled after armour ({@link PrimordialEntity#scaleDamage}):
 * NeoForge applies armour, enchantments and absorption first and fires {@link LivingDamageEvent.Pre} with the amount
 * that is about to come off its health.
 */
public final class BossEvents {
    private BossEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(BossEvents::onDamagePre);
    }

    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof PrimordialEntity boss && !boss.level().isClientSide) {
            event.setNewDamage(boss.scaleDamage(event.getSource(), event.getNewDamage()));
        }
    }
}
