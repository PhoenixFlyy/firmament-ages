package dev.firmages.core.compat.ftbquests;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.immersion.PinnedLines;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's pinned quests for the Age sign (SPEC §18.4, round G2). FTB Quests 2101.1.36 [verified, javap]:
 * {@code ClientQuestFile.selfTeamData.getPinnedQuestIds(Player)} holds the pinned quest ids, and the id
 * {@code TeamData.AUTO_PIN_ID} stands for auto-pin, which FTB's own tracker ({@code PinnedQuestsTracker.collectPinnedQuests})
 * resolves to every visible, startable quest not completed yet. Progress as in that tracker: {@code TeamData.isCompleted},
 * {@code getProgress(Task)} with {@code Task.formatProgress} and {@code formatMaxProgress}. Client only; the FTB classes
 * load only through {@link #lines()} when FTB Quests is present. Read at most twice a second.
 */
public final class PinnedQuests {
    private static final long REFRESH_MS = 500;
    private static long readAt = Long.MIN_VALUE;
    private static List<PinnedLines.Line> cached = List.of();
    private static boolean failed;

    private PinnedQuests() {}

    /** The sign's lines for the pinned quests, empty without FTB Quests, without a synced quest file or without pins. */
    public static List<PinnedLines.Line> lines() {
        if (failed || !ModList.get().isLoaded("ftbquests")) return List.of();
        long now = Util.getMillis();
        if (now - readAt < REFRESH_MS && readAt != Long.MIN_VALUE) return cached;
        readAt = now;
        try {
            cached = PinnedLines.lines(Impl.read());
        } catch (RuntimeException | LinkageError e) {
            failed = true; // a changed FTB Quests: the sign keeps the Age line, once logged
            FirmagesCore.LOGGER.error("Cannot read the pinned quests of FTB Quests; the Age sign shows none", e);
            cached = List.of();
        }
        return cached;
    }

    private static final class Impl {
        static List<PinnedLines.Quest> read() {
            Player player = Minecraft.getInstance().player;
            if (player == null || !ClientQuestFile.exists()) return List.of();
            ClientQuestFile file = ClientQuestFile.INSTANCE;
            TeamData team = file.selfTeamData;
            if (team == null) return List.of();
            LongSet pinned = team.getPinnedQuestIds(player);
            if (pinned.isEmpty()) return List.of();
            java.util.Set<Quest> quests = new java.util.LinkedHashSet<>();
            if (pinned.contains(TeamData.AUTO_PIN_ID)) {
                file.forAllQuests(q -> {
                    if (!team.isCompleted(q) && q.isVisible(team) && team.canStartTasks(q)) quests.add(q);
                });
            }
            pinned.forEach((long id) -> {
                if (id == TeamData.AUTO_PIN_ID) return;
                Quest q = file.getQuest(id);
                if (q != null) quests.add(q);
            });
            List<PinnedLines.Quest> out = new ArrayList<>();
            for (Quest q : quests) {
                List<Task> tasks = q.getTasksAsList();
                int done = 0;
                for (Task t : tasks) if (team.isCompleted(t)) done++;
                String progress = "";
                String max = "";
                if (tasks.size() == 1) {
                    Task t = tasks.getFirst();
                    progress = t.formatProgress(team, team.getProgress(t));
                    max = t.formatMaxProgress();
                }
                out.add(new PinnedLines.Quest(q.getTitle().getString(), team.isCompleted(q), tasks.size(), done, progress, max));
            }
            return out;
        }
    }
}
