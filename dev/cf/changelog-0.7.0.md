# Firmament Ages 0.7.0

Beta: the whole progression is playable from Dawn to the final boss, balance and texts will still change. This changelog covers everything since 0.5.0 (the first release candidate that was never published).

## 0.7.0: Immersion

- **The world first, the machinery later.** In Dawn nothing of the pack's inner workings shows: no mod messages or popups on joining, no advancement toasts or chat lines, no advancement screen (the quest book opens in its place), no mod lists or mod names in menus, tooltips or overlays. The recipe viewer (EMI) and the block info overlay (Jade) appear with the Stone Age; the server decides this per world (Firmament Ages Core config `ui.emiFromAge`, `ui.jadeFromAge`).
- **Pack-styled loading and title screens.** A Firmament loading screen from the first second (Drippy Loading Screen), a title screen with a slideshow of the Shrine through the Ages, a restyled pause menu, no Mods or Realms buttons and no mod count (FancyMenu).
- **Default options for new installs:** sprint on Left Shift, sneak on Left Control, every key conflict of the pack resolved (Create, Mekanism, Immersive Engineering, Ars Nouveau and others), GUI scale 3, no tutorial or onboarding popups, dark loading background. Existing installs keep their own options.
- **The Age indicator.** A small line top left names your Age and the offering it asks for; it fades out a few seconds after you join or an Age opens, and shows while you hold **H**.
- **A welcome for every new player:** a title, then a one-time screen that points to the quest book (**K**) and the Shrine.
- **The Firmament Chronicle.** The quest book has its own look: night-sky backgrounds with the Shrine of each Age, Age banners, gilded and rune quest shapes, and a moving starfield behind every chapter (Certain Questing Additions). Only the chapters of Ages you have reached are listed; later Ages stay hidden, not greyed out. The Welcome chapter is rewritten in the voice of the world.
- **TerraFirmaCraft survival without thirst and without a nutrition penalty.** Thirst never drops and its bar is gone; a one-sided diet never lowers your health below the starting value, while a varied diet still raises it. The nutrition tab and the nutrition lines in food tooltips are gone. Food decay and tool durability are unchanged.
- **Items arrive when they can first be made or found.** Every vanilla and mod item now belongs to the first Age in which a recipe or a world source can actually produce it: about 1,800 items moved to a later Age (for example the crafting table, furnace, torch and bucket), and about 3,900 items that can never be obtained in this pack are disabled and hidden from EMI.
- **Firmament Ages Core 0.7.0** with all of the above.
- New mods: FancyMenu, Konkrete, Melody, Drippy Loading Screen, Drippy Early Loading Module and Certain Questing Additions (all client side).

## 0.6.3

- **No more extra freeze when an Age unlocks.** Firmament Ages Core 0.6.2: ProgressiveStages used to resend its full lock list to every team member once for each stage an Age grant changes (the Age, the mob ladder, the helper stages), which froze the server for about three seconds per unlock with two players, on top of the datapack reload. The mod now sends one lock update and one stage update per player at the end of the tick; on the test server that freeze dropped from about three seconds to about one with two players.
- The pack's own parts (quests, scripts, configs, textures, Firmament Ages Core) carry an MIT license file in the source repository.

## 0.6.2

- **Osmium is the pack's own ore.** The Information Age's osmium now comes from a TerraFirmaCraft vein of the pack: poor, normal and rich ore in ten igneous and metamorphic rocks, small surface and cave pieces as indicators, hidden until the Electric Age as before. The pieces melt in a crucible and cast into Mekanism osmium ingots, two ingots weld into an osmium double ingot (osmium ingots can now be heated, so the weld works), and the Mekanism ore ladder takes the new pieces. MekaTFC is no longer part of the pack.
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
