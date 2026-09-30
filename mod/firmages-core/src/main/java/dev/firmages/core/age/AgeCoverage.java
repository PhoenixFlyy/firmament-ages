package dev.firmages.core.age;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Startup coverage check (SPEC §2.1): untagged items count as always unlocked, so every item that ProgressiveStages
 * locks behind an Age but no age tag contains is a gap. Also reports items whose tag Age differs from the PS lock
 * Age, and PS ore overrides whose block is missing from {@code age_blocks}. Full list in
 * {@code logs/firmages-coverage.txt}.
 */
public final class AgeCoverage {
    public static final String REPORT_FILE = "firmages-coverage.txt";

    public record Result(List<String> untaggedItems, List<String> ageMismatches, List<String> untaggedOreBlocks, int psAgeLocks) {
        public boolean clean() {
            return untaggedItems.isEmpty() && ageMismatches.isEmpty() && untaggedOreBlocks.isEmpty();
        }
    }

    private static volatile Result last;

    private AgeCoverage() {}

    public static Result last() {
        return last;
    }

    public static Result check(AgeIndex index) {
        List<String> untagged = new ArrayList<>();
        List<String> mismatch = new ArrayList<>();
        List<String> ores = new ArrayList<>();
        int psAgeLocks = 0;
        try {
            LockRegistry reg = LockRegistry.getInstance();
            for (Map.Entry<ResourceLocation, StageId> e : reg.getAllResolvedItemLocks().entrySet()) {
                Optional<AgeId> psAge = AgeId.byId(e.getValue().getPath());
                if (psAge.isEmpty()) continue; // tool_*, mob_*, disabled: not an Age lock
                psAgeLocks++;
                Item item = BuiltInRegistries.ITEM.get(e.getKey());
                AgeId tagAge = index.ageOf(item);
                if (tagAge == null) untagged.add(e.getKey() + " (PS " + psAge.get().id() + ")");
                else if (tagAge != psAge.get()) mismatch.add(e.getKey() + " tag " + tagAge.id() + " vs PS " + psAge.get().id());
            }
            for (LockRegistry.OreOverrideEntry o : reg.getOreOverrides()) {
                Optional<AgeId> psAge = AgeId.byId(o.requiredStage.getPath());
                if (psAge.isEmpty()) continue;
                Block block = BuiltInRegistries.BLOCK.get(o.target);
                if (index.ageOf(block) == null) ores.add(o.target + " (PS " + psAge.get().id() + ")");
            }
        } catch (RuntimeException | LinkageError e) {
            FirmagesCore.LOGGER.warn("Age coverage check skipped: {}", e.toString());
            return null;
        }
        Result r = new Result(List.copyOf(untagged), List.copyOf(mismatch), List.copyOf(ores), psAgeLocks);
        last = r;
        report(r);
        return r;
    }

    private static void report(Result r) {
        if (r.clean()) {
            FirmagesCore.LOGGER.info("Age coverage: all {} ProgressiveStages Age item locks are in an age tag", r.psAgeLocks());
        } else {
            FirmagesCore.LOGGER.warn("Age coverage: {} PS-locked items without age tag, {} with a different Age, {} ore-override blocks without age_blocks tag"
                + " (of {} PS Age item locks); first: {}; full list in logs/{}", r.untaggedItems().size(), r.ageMismatches().size(),
                r.untaggedOreBlocks().size(), r.psAgeLocks(), r.untaggedItems().subList(0, Math.min(10, r.untaggedItems().size())), REPORT_FILE);
        }
        List<String> lines = new ArrayList<>();
        lines.add("# firmages-core Age coverage: ProgressiveStages Age locks vs firmages:age_* tags");
        lines.add("# PS Age item locks: " + r.psAgeLocks());
        lines.add("");
        lines.add("## PS-locked items missing from every age_items tag (count as always unlocked): " + r.untaggedItems().size());
        lines.addAll(r.untaggedItems());
        lines.add("");
        lines.add("## Items whose age tag differs from the PS lock stage: " + r.ageMismatches().size());
        lines.addAll(r.ageMismatches());
        lines.add("");
        lines.add("## PS ore-override blocks missing from every age_blocks tag: " + r.untaggedOreBlocks().size());
        lines.addAll(r.untaggedOreBlocks());
        Path file = FMLPaths.GAMEDIR.get().resolve("logs").resolve(REPORT_FILE);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            FirmagesCore.LOGGER.warn("Cannot write {}: {}", file, e.toString());
        }
    }
}
