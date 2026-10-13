# Firmament Ages 0.6.2

First public release on CurseForge, as a beta: the whole progression is playable from Dawn to the final boss, balance and texts will still change. This changelog covers everything since 0.5.0 (the first release candidate that was never published).

## 0.6.2

- **Osmium is the pack's own ore.** The Information Age's osmium now comes from a TerraFirmaCraft vein of the pack (rock variants and grades like every other TFC ore); MekaTFC is no longer part of the pack. The osmium processing recipes follow the new ore.
- **TFC Ore Tooltips removed.** The TerraFirmaCraft field guide lists the host rocks of every ore; nothing else depended on the mod.
- **30 mod updates** (2026-10-13), among them ProgressiveStages 3.0.6, Stargate Journey 0.6.51, Occultism 1.224.5, Ars Nouveau 5.13.3, Theurgy 1.78.0, JEI 19.64, Sophisticated Backpacks 3.26.12, More Mekanism Processing 6.8, ModernFix 5.27.26 and FramedBlocks 10.6.3.
- The ingot crushing that More Mekanism Processing 6.8 added for antimony, boron, magnesium, palladium and thorium is removed, like the other ingot-to-dust shortcuts of the ore ladder.
- The AdvancedAE Strength Card is disabled: it only upgrades the Quantum Armor, which the pack does not use.
- A server pack (ready-to-run server folder with all server jars, configs, scripts and start files) is attached to the release as an additional file.
- The Age-grant freeze: the firmages-core fix that sends the stage packets in one pass is in work for the next core release.

## 0.6.1

- **Boss fallback gateways.** Eight sigils (Frontier, Wild, Forge, Wither, End, Space, Abyss, Chaos), one per Age from the Iron Age on, open a Gateways to Eternity trial that drops the boss material of that Age. For a team that cannot find or beat the Age's boss in the open world.
- **Leftover recipes.** About 100 recipes asked for dead ingredients (disabled Create sheets, vanilla copper and gold ingots, the iron block, lapis); they now take the common tags, so TFC metals fit. Four sequenced assemblies lost their chance byproducts of later Ages, Create's brass nugget comes from TFC brass, Ad Astra plate blocks take rods, and the Soul Extractor of Mystical Agriculture takes a soulium ingot.
- **Firmament Ages Core 0.6.1:** a dedicated server runs one warm-up reload right after it starts, while nobody is online, so the first Age unlock players wait for is not the slow cold one; the ProgressiveStages lock sync on joining a world, changing dimension or respawning is sent once instead of twice.

## 0.6.0

- **Consecration.** When Caelum accepts a ring, its blocks turn, from the heart outward, into one sky-marble structure with the Age's colour accent. Consecrated blocks cannot be broken, blown up or moved. Sneak and punch the Shrine Heart with an empty hand for 60 seconds of maintenance mode; then they break and drop their original material. Rings built before the update are consecrated when the shrine next checks them.
- **Textures v2** for the pack's own items and blocks: redrawn signature items, animated Arcane Keystone, Humming Core, Data Matrix, Star Chart, Quantum Core and Ultimate Singularity, the lit altar top, the sigils and the consecrated block family.
- **Firmament Ages Core 0.6.0** with the consecration, released under the MIT License.
- All mods are now installed from CurseForge; six mods whose CurseForge files are not available to third-party installers (Alternating Flux, Create Aeronautics, Create: Design n' Decor, Create Factory Logistics, Entity Culling, TFC Ruins) are the same files from Modrinth in the GitHub install. Gateways to Eternity uses the CurseForge build of 5.1.0.

## 0.5.0 (never published)

- Dawn and ten Ages, Stone to Singularity Age, each with its quest chapter on **K** (main strands, side lines, one goal quest) and its signature item.
- The Shrine of Caelum with rings 0 to 8: offerings, rites, team prayer, the Age ceremony and one blessing per ring (sanctuary, faster walking, more health, more XP, faster mining, longer reach, softer falls, luck). The Arcane Keystone can be borrowed in the Quantum Age.
- Age gating everywhere: locked items, blocks and recipes are hidden in EMI; machines of every mod refuse recipes of locked Ages; miners and prospecting tools only find the ores of your Ages; mobs and dimensions open Age by Age.
- The finale: Ultimate Singularity by Draconic fusion, a Classic Stargate, The Origin, gateway waves and The Primordial. The Reactor Controller refuels the Draconic Reactor and signals READY by redstone.

## Known issues

See "Beta: what to expect" in the project description.
