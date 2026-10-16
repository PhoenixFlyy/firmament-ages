package dev.firmages.core.anchor;

import dev.firmages.core.shrine.BlessingIndex;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus wiring of the Sky Anchors (SPEC-himmel §29) and their blessing source provider. */
public final class AnchorEvents {
    private AnchorEvents() {}

    public static void register(IEventBus bus) {
        BlessingIndex.registerProvider(AnchorService::sources);
        bus.addListener((ServerTickEvent.Post e) -> AnchorService.tick(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> AnchorService.clear());
    }
}
