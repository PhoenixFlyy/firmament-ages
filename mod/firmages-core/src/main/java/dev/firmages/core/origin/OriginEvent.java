package dev.firmages.core.origin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

import java.util.List;

/**
 * Hooks of the script-first end boss (SPEC §16.3), posted on {@code NeoForge.EVENT_BUS}. KubeJS listens with
 * {@code NativeEvents.onEvent('dev.firmages.core.origin.OriginEvent$Gathering', e => ...)}; datapacks get the same
 * moments as the function tags {@code #firmages:origin/start} and {@code #firmages:origin/won}. {@code SummonBoss} is
 * the operators' shortcut to the boss ({@code /firmages origin fight}).
 */
public abstract class OriginEvent extends Event {
    private final ServerLevel level;
    private final BlockPos altar;

    protected OriginEvent(ServerLevel level, BlockPos altar) {
        this.level = level;
        this.altar = altar;
    }

    /** The Origin. */
    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getAltar() {
        return altar;
    }

    /** Every online player stands at the altar: the boss script starts the fight. Not posted once the boss fell. */
    public static final class Gathering extends OriginEvent {
        private final List<ServerPlayer> players;
        private final int count;

        public Gathering(ServerLevel level, BlockPos altar, List<ServerPlayer> players, int count) {
            super(level, altar);
            this.players = List.copyOf(players);
            this.count = count;
        }

        public List<ServerPlayer> getPlayers() {
            return players;
        }

        /** 1 for the first Gathering, 2 after a failed attempt, ... */
        public int getCount() {
            return count;
        }
    }

    /**
     * {@code /firmages origin fight} (operators): the boss script summons its final boss at once, without the waves.
     * The script answers with {@link #summoned()} or {@link #refuse(String)}; nobody answering means no script listens.
     */
    public static final class SummonBoss extends OriginEvent {
        private boolean summoned;
        private String refusal = "";

        public SummonBoss(ServerLevel level, BlockPos altar) {
            super(level, altar);
        }

        /** The script summoned the boss. */
        public void summoned() {
            summoned = true;
        }

        /** The script did not summon (its fight is running, ...); {@code why} goes to the operator. */
        public void refuse(String why) {
            refusal = why == null ? "" : why;
        }

        public boolean isSummoned() {
            return summoned;
        }

        public String getRefusal() {
            return refusal;
        }

        /** Some listener answered. */
        public boolean answered() {
            return summoned || !refusal.isEmpty();
        }
    }

    /** An entity with the scoreboard tag {@code firmages.final_boss} died; {@code finale_won} is granted. */
    public static final class Victory extends OriginEvent {
        private final LivingEntity boss;

        public Victory(ServerLevel level, BlockPos altar, LivingEntity boss) {
            super(level, altar);
            this.boss = boss;
        }

        public LivingEntity getBoss() {
            return boss;
        }
    }
}
