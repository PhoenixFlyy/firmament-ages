package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.hardship.CaveDwellerGate;
import dev.firmages.core.hardship.CaveDwellerService;
import dev.firmages.core.immersion.AgeStatus;
import dev.firmages.core.immersion.ImmersionService;
import dev.firmages.core.net.AgeStatusPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Level G tests of the hardship gates (SPEC §19). Cave Dweller ReEvolved is not part of the GameTest run, so the
 * real spawn path runs only when its entity is registered; otherwise the test covers the Age decision on the live
 * global Age state, the synced status the client mutes by, and that other entities are never refused.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class HardshipGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(HardshipGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean cond, String what) {
        if (!cond) h.fail(what);
    }

    private static AgeStatus sync(MinecraftServer server, AgeStatus status) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        AgeStatusPayload.STREAM_CODEC.encode(buf, AgeStatusPayload.of(status));
        return AgeStatusPayload.STREAM_CODEC.decode(buf).status();
    }

    private static <T extends Entity> T create(GameTestHelper h, EntityType<T> type) {
        T e = type.create(h.getLevel());
        if (e == null) h.fail("cannot create " + BuiltInRegistries.ENTITY_TYPE.getKey(type));
        BlockPos p = h.absolutePos(new BlockPos(1, 1, 1));
        e.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        return e;
    }

    /**
     * H12: the dweller is refused once the world's Age reaches {@code hardship.caveDwellerUntilAge} (default age_2),
     * {@code never} keeps it, the status the client mutes by follows the same decision through encoding and decoding,
     * and a pig is never refused. With Cave Dweller ReEvolved loaded: a dweller cannot join, and one that joined
     * while allowed is removed by the Age-change hook.
     */
    @GameTest(template = "empty", batch = "firmages_7_hardship")
    public static void caveDwellerGoneFromItsAge(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        AgeId current = AgeStatus.current(AgeService.state(server).snapshot());
        ok(h, List.of("hardship", "caveDwellerUntilAge").equals(ServerConfig.HARDSHIP_CAVE_DWELLER_UNTIL.getPath())
            && "age_2".equals(ServerConfig.HARDSHIP_CAVE_DWELLER_UNTIL.getDefault()),
            "[hardship] caveDwellerUntilAge = age_2, got " + ServerConfig.HARDSHIP_CAVE_DWELLER_UNTIL.getPath() + " = "
            + ServerConfig.HARDSHIP_CAVE_DWELLER_UNTIL.getDefault());
        String was = ServerConfig.HARDSHIP_CAVE_DWELLER_UNTIL.get();
        try {
            ServerConfig.HARDSHIP_CAVE_DWELLER_UNTIL.set("age_2");
            boolean iron = current.ordinal() >= AgeId.AGE_2.ordinal();
            ok(h, CaveDwellerService.over(server) == iron, "age_2: gone exactly from the Iron Age (world in " + current + ")");
            ok(h, ImmersionService.status(server).caveDweller() == !iron, "age_2: status follows");

            ServerConfig.HARDSHIP_CAVE_DWELLER_UNTIL.set("never");
            AgeStatus kept = ImmersionService.status(server);
            ok(h, !CaveDwellerService.over(server) && kept.caveDweller() && sync(server, kept).caveDweller(), "never: it hunts in " + current);

            ServerConfig.HARDSHIP_CAVE_DWELLER_UNTIL.set("dawn");
            AgeStatus gone = ImmersionService.status(server);
            ok(h, CaveDwellerService.over(server) && !gone.caveDweller() && !sync(server, gone).caveDweller(),
                "dawn: gone in every Age, the client gets caveDweller = false");

            Entity pig = create(h, EntityType.PIG);
            ok(h, !CaveDwellerService.isDweller(pig) && level.addFreshEntity(pig) && !pig.isRemoved(), "a pig is never refused");
            pig.discard();

            Optional<EntityType<?>> dweller = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(CaveDwellerGate.ENTITY_ID));
            if (dweller.isPresent()) {
                Entity refused = create(h, dweller.get());
                ok(h, CaveDwellerService.isDweller(refused) && !level.addFreshEntity(refused), "a dweller cannot join once its Age is over");
                ServerConfig.HARDSHIP_CAVE_DWELLER_UNTIL.set("never");
                Entity hunting = create(h, dweller.get());
                ok(h, level.addFreshEntity(hunting), "never: a dweller joins");
                ServerConfig.HARDSHIP_CAVE_DWELLER_UNTIL.set("dawn");
                ok(h, CaveDwellerService.despawnIfOver(server) >= 1 && hunting.isRemoved(), "the Age-change hook removes it");
            } else {
                ok(h, CaveDwellerService.despawnIfOver(server) == 0, "without Cave Dweller ReEvolved there is nothing to remove");
            }
        } finally {
            ServerConfig.HARDSHIP_CAVE_DWELLER_UNTIL.set(was);
        }
        h.succeed();
    }
}
