package dev.firmages.core.balance;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.firmages.core.FirmagesCore;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /firmages energy reference run <name>|all} (harmony package A8, SPEC §38.1): places a reference build from a
 * datapack, prepares it (fuel, coolant, a formed multiblock, a started reactor), lets it settle, meters it with
 * {@link EnergyAudit} and writes the result to {@code logs/firmages-energy-reference.json}. Dev tool for copies of the
 * test server, only with {@code debug.allowSimulate}: the builds and their run files come from
 * {@code dev/gen_reference_builds.py}, which installs them as a world datapack ({@code data/<ns>/structure/reference/}
 * and {@code data/<ns>/firmages_energy_reference/}); the pack ships none.
 *
 * <p>A run file names the template, the audit centre and radius, the warm-up and the run time, the steps and what to
 * read: FE per tick that reached the {@code sinks} (their "in"), minus what the {@code sources} gave (their "out") and
 * what the {@code feeds} took (their "in": energy fed in from outside, such as a field injector), plus the net use of
 * the {@code users} (consumers). The steps
 * fill fluids, chemicals and items through the capabilities, use an item on a block (an IE hammer forms a multiblock),
 * merge block entity NBT, run a command at the site and drive Mekanism and Draconic Evolution reactors
 * ({@code compat/mekanism/MekReference}, {@code compat/draconic/DraconicReference}). A step with {@code every} repeats
 * that often until the run ends (coolant and fuel feeds).
 */
public final class ReferenceRun {
    public static final String DIR = "firmages_energy_reference";
    private static final int SITE_X = 30_000;
    private static final int SITE_Z = 30_000;
    private static final int SITE_Y = 200;
    private static final int SITE_STEP = 160;
    private static final Deque<Job> QUEUE = new ArrayDeque<>();
    @Nullable
    private static Job current;
    private static final List<JsonObject> RESULTS = new ArrayList<>();
    /** FE a step put into the build outside any energy capability during the audit (a reactor's field). */
    private static long fedDuringAudit;

    /** A step fed {@code fe} FE into the build past the capabilities; counted against the output while the audit runs. */
    public static void fed(long fe) {
        Job j = current;
        if (j != null && j.phase == 4) fedDuringAudit += fe;
    }

    private ReferenceRun() {}

    /** A loaded run file. */
    public record Recipe(ResourceLocation id, JsonObject json) {
        String name() {
            String p = id.getPath();
            return p.substring(p.lastIndexOf('/') + 1, p.length() - ".json".length());
        }

        ResourceLocation template() {
            return json.has("template") ? ResourceLocation.parse(json.get("template").getAsString())
                : ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "reference/" + name());
        }

        int i(String key, int def) {
            return json.has(key) ? json.get(key).getAsInt() : def;
        }
    }

    /** Every run file in the loaded datapacks, by name. */
    public static Map<String, Recipe> recipes(MinecraftServer server) {
        Map<String, Recipe> out = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : server.getResourceManager().listResources(DIR, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            try (Reader r = e.getValue().openAsReader()) {
                Recipe rec = new Recipe(e.getKey(), JsonParser.parseReader(r).getAsJsonObject());
                out.put(rec.name(), rec);
            } catch (Exception ex) {
                FirmagesCore.LOGGER.warn("Energy reference: cannot read {}: {}", e.getKey(), ex.toString());
            }
        }
        return out;
    }

    /** Queues the runs; they start on the next server tick, one after the other. */
    public static int queue(CommandSourceStack src, List<Recipe> recipes, int secondsOverride) {
        int base = QUEUE.size() + (current == null ? 0 : 1) + RESULTS.size();
        int n = 0;
        for (Recipe r : recipes) QUEUE.add(new Job(r, src, base + n++, secondsOverride));
        return n;
    }

    public static boolean busy() {
        return current != null || !QUEUE.isEmpty();
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (current == null) {
            if (QUEUE.isEmpty()) return;
            current = QUEUE.poll();
        }
        Job j = current;
        try {
            if (j.tick(event.getServer())) {
                current = null;
                j.release();
            }
        } catch (RuntimeException e) {
            FirmagesCore.LOGGER.error("Energy reference {} failed", j.recipe.name(), e);
            j.src.sendFailure(Component.literal("Energy reference " + j.recipe.name() + " failed: " + e));
            current = null;
            j.release();
        }
    }

    // ------------------------------------------------------------------ one run

    private static final class Job {
        final Recipe recipe;
        final CommandSourceStack src;
        final ServerLevel level;
        final BlockPos origin;
        final int seconds;
        final List<String> log = new ArrayList<>();
        final List<long[]> forced = new ArrayList<>();
        final List<JsonObject> repeats = new ArrayList<>();
        final List<Long> repeatDue = new ArrayList<>();
        int phase; // 0 place, 1 settle, 2 steps, 3 warm-up, 4 audit, 5 done
        long wakeAt;
        int stepIndex;
        @Nullable
        EnergyAudit audit;
        @Nullable
        StructureTemplate template;

        Job(Recipe recipe, CommandSourceStack src, int index, int secondsOverride) {
            this.recipe = recipe;
            this.src = src;
            this.level = src.getServer().overworld();
            this.origin = new BlockPos(SITE_X + index * SITE_STEP, SITE_Y, SITE_Z);
            this.seconds = secondsOverride > 0 ? secondsOverride : recipe.i("seconds", 60);
        }

        BlockPos at(JsonElement e) {
            JsonArray a = e.getAsJsonArray();
            return origin.offset(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
        }

        void say(String line) {
            log.add(line);
            FirmagesCore.LOGGER.info("[energy-reference {}] {}", recipe.name(), line);
        }

        /** Advances the run; true when it is over. */
        boolean tick(MinecraftServer server) {
            long now = level.getGameTime();
            if (now < wakeAt) {
                runRepeats(now);
                return false;
            }
            switch (phase) {
                case 0 -> {
                    template = server.getStructureManager().get(recipe.template()).orElse(null);
                    if (template == null) {
                        src.sendFailure(Component.literal("Energy reference " + recipe.name() + ": no template " + recipe.template()));
                        say("no template " + recipe.template());
                        return true;
                    }
                    var size = template.getSize();
                    for (int cx = SectionPos.blockToSectionCoord(origin.getX() - 16); cx <= SectionPos.blockToSectionCoord(origin.getX() + size.getX() + 16); cx++) {
                        for (int cz = SectionPos.blockToSectionCoord(origin.getZ() - 16); cz <= SectionPos.blockToSectionCoord(origin.getZ() + size.getZ() + 16); cz++) {
                            if (level.setChunkForced(cx, cz, true)) forced.add(new long[] {cx, cz});
                        }
                    }
                    template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1), Block.UPDATE_ALL);
                    say("placed " + recipe.template() + " (" + size.getX() + " x " + size.getY() + " x " + size.getZ() + ") at " + origin.toShortString());
                    phase = 1;
                    wakeAt = now + recipe.i("settle", 40);
                }
                case 1, 2 -> {
                    phase = 2;
                    JsonArray steps = recipe.json.has("steps") ? recipe.json.getAsJsonArray("steps") : new JsonArray();
                    while (stepIndex < steps.size()) {
                        JsonObject s = steps.get(stepIndex++).getAsJsonObject();
                        if ("wait".equals(s.get("op").getAsString())) {
                            wakeAt = now + s.get("ticks").getAsInt();
                            return false;
                        }
                        step(s);
                        if (s.has("every")) {
                            repeats.add(s);
                            repeatDue.add(now + s.get("every").getAsInt());
                        }
                    }
                    phase = 3;
                    wakeAt = now + recipe.i("warmup", 200);
                }
                case 3 -> {
                    probe("start");
                    BlockPos centre = recipe.json.has("centre") ? at(recipe.json.get("centre")) : origin;
                    audit = new EnergyAudit(level, centre, recipe.i("radius", 16), seconds * 20, server.createCommandSourceStack().withSuppressedOutput());
                    EnergyMeter.start(audit);
                    fedDuringAudit = 0;
                    phase = 4;
                }
                case 4 -> {
                    if (EnergyMeter.running() == audit) {
                        runRepeats(now);
                        return false;
                    }
                    probe("end");
                    report();
                    return true;
                }
                default -> {
                    return true;
                }
            }
            return false;
        }

        void runRepeats(long now) {
            for (int i = 0; i < repeats.size(); i++) {
                if (now >= repeatDue.get(i)) {
                    step(repeats.get(i));
                    repeatDue.set(i, now + repeats.get(i).get("every").getAsInt());
                }
            }
        }

        void probe(String when) {
            if (!recipe.json.has("probe")) return;
            for (JsonElement e : recipe.json.getAsJsonArray("probe")) {
                BlockPos p = at(e);
                String s = null;
                if (ModList.get().isLoaded("mekanism")) s = dev.firmages.core.compat.mekanism.MekReference.probe(level, p);
                if (s == null && ModList.get().isLoaded("draconicevolution")) s = dev.firmages.core.compat.draconic.DraconicReference.probe(level, p);
                if (s == null && ModList.get().isLoaded("create")) s = dev.firmages.core.compat.create.CreateReference.probe(level, p);
                if (s == null && ModList.get().isLoaded("immersiveengineering")) s = dev.firmages.core.compat.ie.IeReference.probe(level, p);
                if (s == null) s = fluids(p);
                say("probe " + when + " " + p.subtract(origin).toShortString() + ": " + s);
            }
        }

        /** The block id and what its fluid handler holds (generic probe). */
        String fluids(BlockPos p) {
            StringBuilder out = new StringBuilder(BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString());
            IFluidHandler h = sided(Capabilities.FluidHandler.BLOCK, p, null);
            if (h != null) {
                for (int i = 0; i < h.getTanks(); i++) {
                    FluidStack f = h.getFluidInTank(i);
                    out.append(", tank ").append(i).append(": ").append(f.isEmpty() ? "empty" : f.getAmount() + " mB " + BuiltInRegistries.FLUID.getKey(f.getFluid()));
                }
            }
            return out.toString();
        }

        void step(JsonObject s) {
            String op = s.get("op").getAsString();
            BlockPos p = s.has("at") ? at(s.get("at")) : origin;
            String r;
            switch (op) {
                case "fluid" -> r = fluid(p, s);
                case "item" -> r = item(p, s);
                case "use" -> r = use(p, s);
                case "nbt" -> r = nbt(p, s);
                case "energy" -> r = energy(p, s);
                case "command" -> r = command(s.get("cmd").getAsString());
                default -> {
                    r = null;
                    if (ModList.get().isLoaded("mekanism")) r = dev.firmages.core.compat.mekanism.MekReference.step(op, s, level, p);
                    if (r == null && ModList.get().isLoaded("draconicevolution")) r = dev.firmages.core.compat.draconic.DraconicReference.step(op, s, level, p);
                    if (r == null && ModList.get().isLoaded("immersiveengineering")) r = dev.firmages.core.compat.ie.IeReference.step(op, s, level, p);
                    if (r == null) r = "unknown op " + op;
                }
            }
            if (!s.has("every") || !s.has("quiet")) say(op + " at " + p.subtract(origin).toShortString() + ": " + r);
        }

        @Nullable
        static Direction side(JsonObject s) {
            return s.has("side") ? Direction.byName(s.get("side").getAsString()) : null;
        }

        String fluid(BlockPos p, JsonObject s) {
            var fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(s.get("id").getAsString()));
            int amount = s.get("amount").getAsInt();
            Direction side = side(s);
            Direction[] faces = side != null ? new Direction[] {side} : Direction.values();
            int filled = 0;
            boolean any = false;
            // without a side: every face in turn (some blocks take fluids only through their port faces)
            for (int i = 0; i < faces.length && filled < amount; i++) {
                IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, p, faces[i]);
                if (h == null) continue;
                any = true;
                filled += h.fill(new FluidStack(fluid, amount - filled), IFluidHandler.FluidAction.EXECUTE);
            }
            if (!any) return "no fluid handler";
            return "filled " + filled + " of " + amount + " mB " + s.get("id").getAsString();
        }

        /** FE into the block through its energy capability (metered like any other lookup while the audit runs). */
        String energy(BlockPos p, JsonObject s) {
            int amount = s.get("amount").getAsInt();
            var h = sided(Capabilities.EnergyStorage.BLOCK, p, side(s));
            if (h == null) return "no energy storage";
            return "received " + h.receiveEnergy(amount, false) + " of " + amount + " FE";
        }

        String item(BlockPos p, JsonObject s) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(s.get("id").getAsString()));
            int count = s.get("count").getAsInt();
            IItemHandler h = sided(Capabilities.ItemHandler.BLOCK, p, side(s));
            if (h == null) return "no item handler";
            ItemStack rest = new ItemStack(item, count);
            for (int i = 0; i < h.getSlots() && !rest.isEmpty(); i++) rest = h.insertItem(i, rest, false);
            return "inserted " + (count - rest.getCount()) + " of " + count + " " + s.get("id").getAsString();
        }

        /** A fake player uses an item on the block (top face by default), looking along {@code yaw}. */
        String use(BlockPos p, JsonObject s) {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(s.get("item").getAsString())));
            fp.setItemInHand(InteractionHand.MAIN_HAND, stack);
            fp.setYRot(s.has("yaw") ? s.get("yaw").getAsFloat() : 180f);
            fp.setXRot(s.has("pitch") ? s.get("pitch").getAsFloat() : 0f);
            fp.setPos(Vec3.atCenterOf(p).add(0, 1.5, 0));
            fp.setShiftKeyDown(s.has("sneak") && s.get("sneak").getAsBoolean());
            Direction face = s.has("face") ? Direction.byName(s.get("face").getAsString()) : Direction.UP;
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(p).relative(face, 0.5), face, p, false);
            var result = stack.useOn(new UseOnContext(fp, InteractionHand.MAIN_HAND, hit));
            String after = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
            fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            fp.setShiftKeyDown(false);
            return result + ", now " + after;
        }

        String nbt(BlockPos p, JsonObject s) {
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) return "no block entity";
            try {
                CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                tag.merge(TagParser.parseTag(s.get("merge").getAsString()));
                be.loadWithComponents(tag, level.registryAccess());
                be.setChanged();
                level.sendBlockUpdated(p, be.getBlockState(), be.getBlockState(), Block.UPDATE_ALL);
                return "merged";
            } catch (Exception e) {
                return "bad NBT: " + e.getMessage();
            }
        }

        String command(String cmd) {
            String full = "execute in " + level.dimension().location() + " positioned " + origin.getX() + " " + origin.getY() + " " + origin.getZ() + " run " + cmd;
            try {
                int n = level.getServer().getCommands().getDispatcher().execute(full, level.getServer().createCommandSourceStack().withSuppressedOutput());
                return "result " + n;
            } catch (Exception e) {
                return "failed: " + e.getMessage();
            }
        }

        @Nullable
        <T> T sided(net.neoforged.neoforge.capabilities.BlockCapability<T, Direction> cap, BlockPos p, @Nullable Direction side) {
            T h = level.getCapability(cap, p, side);
            for (int i = 0; h == null && side == null && i < Direction.values().length; i++) h = level.getCapability(cap, p, Direction.values()[i]);
            return h;
        }

        void report() {
            JsonObject json = recipe.json;
            double sinks = 0, sources = 0, feeds = 0, users = 0;
            List<String> rows = new ArrayList<>();
            for (EnergyAudit.TypeFlow f : audit.types()) {
                if (listed(json, "sinks", f.block())) sinks += f.in();
                if (listed(json, "sources", f.block())) sources += f.out();
                if (listed(json, "feeds", f.block())) feeds += f.in();
                if (listed(json, "users", f.block())) users += f.netUse();
                rows.add(String.format(Locale.ROOT, "%s x%d: in %.1f, out %.1f, stored %+.1f FE/t", f.block(), f.count(), f.in(), f.out(), f.stored()));
            }
            double fedPerTick = fedDuringAudit / (double) Math.max(1, audit.ticks());
            double value = sinks - sources - feeds - fedPerTick + users;
            double min = json.has("band") ? json.getAsJsonArray("band").get(0).getAsDouble() : Double.NaN;
            double max = json.has("band") ? json.getAsJsonArray("band").get(1).getAsDouble() : Double.NaN;
            boolean inBand = Double.isNaN(min) || (value >= min && value <= max);
            String head = String.format(Locale.ROOT, "Energy reference %s (%s, %s): %.1f FE/t%s over %d ticks", recipe.name(),
                json.has("age") ? json.get("age").getAsString() : "?", json.has("kind") ? json.get("kind").getAsString() : "generator", value,
                Double.isNaN(min) ? "" : String.format(Locale.ROOT, ", band %.0f to %.0f: %s", min, max, inBand ? "in band" : "OUT OF BAND"), audit.ticks());
            say(head);
            rows.forEach(this::say);
            if (fedDuringAudit > 0) say(String.format(Locale.ROOT, "fed past the capabilities: %.1f FE/t (subtracted)", fedPerTick));
            JsonObject o = new JsonObject();
            o.addProperty("name", recipe.name());
            o.addProperty("template", recipe.template().toString());
            if (json.has("age")) o.add("age", json.get("age"));
            if (json.has("kind")) o.add("kind", json.get("kind"));
            if (json.has("band")) o.add("band", json.get("band"));
            o.addProperty("fe_per_tick", value);
            o.addProperty("fed_fe_per_tick", fedPerTick);
            o.addProperty("in_band", inBand);
            o.addProperty("ticks", audit.ticks());
            o.addProperty("origin", origin.toShortString());
            JsonArray types = new JsonArray();
            for (EnergyAudit.TypeFlow f : audit.types()) {
                JsonObject t = new JsonObject();
                t.addProperty("block", f.block());
                t.addProperty("count", f.count());
                t.addProperty("in", f.in());
                t.addProperty("out", f.out());
                t.addProperty("stored", f.stored());
                types.add(t);
            }
            o.add("types", types);
            JsonArray lines = new JsonArray();
            log.forEach(lines::add);
            o.add("log", lines);
            RESULTS.add(o);
            write(level.getServer());
            src.sendSuccess(() -> Component.literal(head).withStyle(inBand ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        }

        void release() {
            if (recipe.json.has("keep") && recipe.json.get("keep").getAsBoolean()) {
                say("chunks stay forceloaded (keep): the build keeps running after the run");
                return;
            }
            for (long[] c : forced) level.setChunkForced((int) c[0], (int) c[1], false);
            forced.clear();
        }
    }

    static boolean listed(JsonObject json, String key, String block) {
        if (!json.has(key)) return false;
        for (JsonElement e : json.getAsJsonArray(key)) if (e.getAsString().equals(block)) return true;
        return false;
    }

    private static void write(MinecraftServer server) {
        Path file = server.getServerDirectory().resolve("logs").resolve("firmages-energy-reference.json");
        JsonArray arr = new JsonArray();
        RESULTS.forEach(arr::add);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(arr), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FirmagesCore.LOGGER.warn("Energy reference: cannot write {}", file, e);
        }
    }
}
