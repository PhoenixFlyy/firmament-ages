package dev.firmages.core.compat.mekanism;

import com.google.gson.JsonObject;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.Upgrade;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.multiblock.IMultiblock;
import mekanism.common.lib.multiblock.MultiblockData;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.generators.common.content.fission.FissionReactorMultiblockData;
import mekanism.generators.common.content.fusion.FusionReactorMultiblockData;
import mekanism.generators.common.content.turbine.TurbineMultiblockData;
import mekanism.generators.common.tile.TileEntityGasGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Mekanism steps and probes of the energy reference runs ({@code balance/ReferenceRun}, harmony package A8): chemicals
 * through the chemical capability, a fission reactor's burn rate and activation, a fusion reactor's injection rate and
 * ignition (plasma temperature, as the laser would), upgrades, a started Digital Miner; probes read the multiblock
 * (formed, rates, temperatures, tanks) at a casing or port. Mekanism 10.7.19 [verified, javap]. Loaded only with
 * Mekanism; the generator parts only with Mekanism Generators.
 */
public final class MekReference {
    private MekReference() {}

    /** Runs {@code op} at {@code pos}; null when the op is not Mekanism's. */
    @Nullable
    public static String step(String op, JsonObject s, ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        switch (op) {
            case "chemical": {
                Chemical chem = MekanismAPI.CHEMICAL_REGISTRY.get(ResourceLocation.parse(s.get("id").getAsString()));
                long amount = s.get("amount").getAsLong();
                Direction side = s.has("side") ? Direction.byName(s.get("side").getAsString()) : null;
                ChemicalStack stack = new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.wrapAsHolder(chem), amount);
                ChemicalStack rest = stack;
                boolean any = false;
                // without a side: every face in turn (Mekanism's unsided handler refuses automation inserts)
                Direction[] faces = side != null ? new Direction[] {side} : Direction.values();
                for (int i = 0; i < faces.length && !rest.isEmpty(); i++) {
                    IChemicalHandler h = level.getCapability(Capabilities.CHEMICAL.block(), pos, faces[i]);
                    if (h == null) continue;
                    any = true;
                    rest = h.insertChemical(rest, Action.EXECUTE);
                }
                if (!any && be instanceof TileEntityMekanism tile) {
                    // a block whose chemical ports sit on bounding blocks: straight into its tanks
                    for (IChemicalTank t : tile.getChemicalTanks(null)) {
                        rest = t.insert(rest, Action.EXECUTE, AutomationType.INTERNAL);
                        if (rest.isEmpty()) break;
                    }
                    return "tanks took " + (amount - rest.getAmount()) + " of " + amount + " mB " + s.get("id").getAsString();
                }
                if (!any) return "no chemical handler";
                return "inserted " + (amount - rest.getAmount()) + " of " + amount + " mB " + s.get("id").getAsString();
            }
            case "mek_tank": {
                // straight into a multiblock tank, past the port modes (fuel feeds of a running reactor)
                MultiblockData data = data(be);
                if (data == null) return "no formed multiblock";
                IChemicalTank tank = tank(data, s.get("tank").getAsString());
                if (tank == null) return "no tank " + s.get("tank").getAsString();
                Chemical chem = MekanismAPI.CHEMICAL_REGISTRY.get(ResourceLocation.parse(s.get("id").getAsString()));
                long amount = s.get("amount").getAsLong();
                ChemicalStack rest = tank.insert(new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.wrapAsHolder(chem), amount), Action.EXECUTE, AutomationType.INTERNAL);
                return "tank " + s.get("tank").getAsString() + " took " + (amount - rest.getAmount()) + " of " + amount;
            }
            case "mek_fission": {
                if (!ModList.get().isLoaded("mekanismgenerators")) return "no Mekanism Generators";
                if (!(data(be) instanceof FissionReactorMultiblockData f)) return "no formed fission reactor";
                if (s.has("rate")) f.setRateLimit(s.get("rate").getAsDouble());
                if (s.has("active")) f.setActive(s.get("active").getAsBoolean());
                return "rate limit " + f.rateLimit + ", active " + f.isActive() + ", max burn " + f.getMaxBurnRate();
            }
            case "mek_fusion": {
                if (!ModList.get().isLoaded("mekanismgenerators")) return "no Mekanism Generators";
                if (!(data(be) instanceof FusionReactorMultiblockData f)) return "no formed fusion reactor";
                if (s.has("injection")) f.setInjectionRate(s.get("injection").getAsInt());
                if (s.has("plasma")) {
                    // both: burnFuel reads last tick's plasma temperature, so a fresh ignition needs it as well
                    f.setPlasmaTemp(s.get("plasma").getAsDouble());
                    f.setLastPlasmaTemp(s.get("plasma").getAsDouble());
                }
                if (s.has("burning")) f.setBurning(s.get("burning").getAsBoolean());
                return "injection " + f.getInjectionRate() + ", plasma " + f.getPlasmaTemp() + ", burning " + f.isBurning();
            }
            case "capcheck": {
                // which energy capabilities the block answers per side (null side first): strict energy and FE
                StringBuilder out = new StringBuilder();
                java.util.List<Direction> sides = new java.util.ArrayList<>();
                sides.add(null);
                sides.addAll(java.util.List.of(Direction.values()));
                for (Direction d : sides) {
                    var st = level.getCapability(Capabilities.STRICT_ENERGY.block(), pos, d);
                    var fe = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, d);
                    out.append(d == null ? "null" : d.getName()).append(": strict ").append(st == null ? "-" : st.getClass().getSimpleName())
                        .append(st == null ? "" : " needs " + st.getNeededEnergy(0)).append(", FE ").append(fe == null ? "-" : fe.getClass().getSimpleName()).append("; ");
                }
                return out.toString();
            }
            case "mek_port": {
                // a fusion port's output mode (package-private in Mekanism Generators; a sneak-click with a configurator)
                if (be == null || !be.getClass().getSimpleName().equals("TileEntityFusionReactorPort")) return "no fusion reactor port";
                try {
                    java.lang.reflect.Method m = be.getClass().getDeclaredMethod("setMode", boolean.class);
                    m.setAccessible(true);
                    m.invoke(be, s.get("output").getAsBoolean());
                    return "output mode " + portMode(be);
                } catch (ReflectiveOperationException e) {
                    return "cannot set the mode: " + e;
                }
            }
            case "mek_upgrades": {
                if (!(be instanceof TileEntityMekanism tile) || !tile.supportsUpgrades()) return "no upgradeable Mekanism block";
                StringBuilder out = new StringBuilder();
                for (String key : s.keySet()) {
                    if (key.equals("op") || key.equals("at")) continue;
                    Upgrade u = Upgrade.valueOf(key.toUpperCase(Locale.ROOT));
                    int added = tile.getComponent().addUpgrades(u, s.get(key).getAsInt());
                    out.append(u).append(" +").append(added).append(' ');
                }
                return out.toString().trim();
            }
            case "mek_miner": {
                if (!(be instanceof TileEntityDigitalMiner miner)) return "no Digital Miner";
                if (s.has("radius")) miner.setRadiusFromPacket(s.get("radius").getAsInt());
                if (s.has("min_y")) miner.setMinYFromPacket(s.get("min_y").getAsInt());
                if (s.has("max_y")) miner.setMaxYFromPacket(s.get("max_y").getAsInt());
                if (s.has("inverse") && s.get("inverse").getAsBoolean() != miner.getInverse()) miner.toggleInverse();
                if (s.has("start") && s.get("start").getAsBoolean()) miner.start();
                return "radius " + miner.getRadius() + ", y " + miner.getMinY() + ".." + miner.getMaxY() + ", inverse " + miner.getInverse();
            }
            default:
                return null;
        }
    }

    static String portMode(BlockEntity be) {
        try {
            java.lang.reflect.Method m = be.getClass().getDeclaredMethod("getMode");
            m.setAccessible(true);
            return String.valueOf(m.invoke(be));
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }

    @Nullable
    private static MultiblockData data(@Nullable BlockEntity be) {
        if (!(be instanceof IMultiblock<?> mb)) return null;
        MultiblockData d = mb.getMultiblock();
        return d != null && d.isFormed() ? d : null;
    }

    @Nullable
    private static IChemicalTank tank(MultiblockData data, String name) {
        if (!ModList.get().isLoaded("mekanismgenerators")) return null;
        if (data instanceof FissionReactorMultiblockData f) return "fuel".equals(name) ? f.fuelTank : null;
        if (data instanceof FusionReactorMultiblockData f) {
            return switch (name) {
                case "deuterium" -> f.deuteriumTank;
                case "tritium" -> f.tritiumTank;
                case "fuel" -> f.fuelTank;
                default -> null;
            };
        }
        return null;
    }

    /** One line about the Mekanism block at {@code pos}, or null. */
    @Nullable
    public static String probe(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMultiblock<?> mb) {
            MultiblockData d = mb.getMultiblock();
            if (d == null || !d.isFormed()) return be.getBlockState().getBlock() + " not formed";
            if (ModList.get().isLoaded("mekanismgenerators")) {
                String g = generatorsProbe(d);
                if (g != null) return be.getClass().getSimpleName().equals("TileEntityFusionReactorPort") ? g + ", port output " + portMode(be) : g;
            }
            return d.getClass().getSimpleName() + " formed";
        }
        if (be instanceof TileEntityDigitalMiner m) {
            return String.format(Locale.ROOT, "digital miner: radius %d, inverse %s, speed %d, energy %d upgrades, energy %d J stored",
                m.getRadius(), m.getInverse(), m.getComponent().getUpgrades(Upgrade.SPEED), m.getComponent().getUpgrades(Upgrade.ENERGY),
                m.getEnergyContainer() == null ? -1 : m.getEnergyContainer().getEnergy());
        }
        if (ModList.get().isLoaded("mekanismgenerators") && be instanceof TileEntityGasGenerator g) {
            return String.format(Locale.ROOT, "gas-burning generator: generation %d J/t, used %.3f mB/t, fuel %d mB, burn ticks %d",
                g.getGenerationRate(), g.getUsed(), g.fuelTank.getStored(), g.getMaxBurnTicks());
        }
        if (be instanceof TileEntityMekanism t) {
            StringBuilder tanks = new StringBuilder();
            for (IChemicalTank c : t.getChemicalTanks(null)) tanks.append(", chemical ").append(c.getStored()).append(" of ").append(c.getCapacity());
            for (var e : t.getEnergyContainers(null)) tanks.append(", energy ").append(e.getEnergy()).append(" J");
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString() + tanks + (t.supportsUpgrades() ? ", upgrades " + t.getComponent().getInstalledTypes() : "");
        }
        return null;
    }

    @Nullable
    private static String generatorsProbe(MultiblockData d) {
        if (d instanceof FissionReactorMultiblockData f) {
            return String.format(Locale.ROOT, "fission: active %s, rate limit %.2f, burn %.3f mB/t, boil %d mB/t, efficiency %.3f, temp %.0f K, damage %.1f %%, "
                    + "fuel %d, coolant %d, heated %d, waste %d, assemblies max burn %d",
                f.isActive(), f.rateLimit, f.lastBurnRate, f.lastBoilRate, f.getBoilEfficiency(), f.heatCapacitor.getTemperature(), f.reactorDamage,
                f.fuelTank.getStored(), f.coolantTank.getFluidTank().getFluidAmount() + f.coolantTank.getChemicalTank().getStored(),
                f.heatedCoolantTank.getStored(), f.wasteTank.getStored(), f.getMaxBurnRate());
        }
        if (d instanceof TurbineMultiblockData t) {
            return String.format(Locale.ROOT, "turbine: blades %d, coils %d, vents %d, dispersers %d, lower volume %d, steam in %d mB/t, flow %d mB/t, "
                    + "steam %d of %d, energy %d of %d J, max production %d J/t, max flow %d mB/t",
                t.blades, t.coils, t.vents, t.getDispersers(), t.lowerVolume, t.lastSteamInput, t.clientFlow, t.chemicalTank.getStored(),
                t.getSteamCapacity(), t.energyContainer.getEnergy(), t.getEnergyCapacity(), t.getMaxProduction(), t.getMaxFlowRate());
        }
        if (d instanceof FusionReactorMultiblockData f) {
            return String.format(Locale.ROOT, "fusion: burning %s, injection %d, plasma %.3e K, case %.3e K, passive %d J/t (Mekanism's figure, current), "
                    + "steam %d mB/t (current), D %d, T %d, D-T %d, water %d, steam %d, energy %d J",
                f.isBurning(), f.getInjectionRate(), f.getPlasmaTemp(), f.getCaseTemp(), f.getPassiveGeneration(false, true), f.getSteamPerTick(true),
                f.deuteriumTank.getStored(), f.tritiumTank.getStored(), f.fuelTank.getStored(), f.waterTank.getFluidAmount(), f.steamTank.getStored(),
                f.energyContainer.getEnergy());
        }
        return null;
    }
}
