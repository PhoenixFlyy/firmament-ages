# Firmament Ages changelog

Beta: the whole progression is playable from Dawn to the final boss, balance and texts will still change. Newest version on top; a CurseForge upload takes the newest section. The log reaches back to 0.5.0, the first release candidate, which was never published.

## 0.9.0

- **Age-ups without a freeze.** A new Age no longer reloads every datapack. The server filters the recipes it loaded at the start again, updates the ore lists of the prospecting tools and the Digital Miner and the recipe caches of the machines, and sends only the recipes. On the test server this takes milliseconds instead of the former freeze of 9 to 15 seconds. `/reload` works as before; `gate.ageSwap = false` in `config/firmages-server.toml` brings back the full reload.
- **The recipe viewer follows the Age everywhere.** From the Bronze Age on EMI shows nothing of a later Age: no items, fluids, Mekanism chemicals, books, enchantments or potions of later mods, no recipe with a locked ingredient, no locked workstation or category, no loot pages of later mods. EMI reloads once per Age instead of twice. In Dawn and the Stone Age no EMI recipe window opens, also not from the click areas of other screens.
- **Ponder and GuideME by Age.** `/ponder` only says how to ponder an item (hold the Ponder key on it), the Ponder index shows unlocked items only, and `/guidemec` is gone. Holding the key on an item still opens its scenes.
- **Fluids have Ages.** Every one of the 758 fluids belongs to an Age; a recipe that makes a fluid of a later Age waits for that Age.
- **Machines wait for their Age.** Create's deployer, spout, item drain and crushing wheel recipes come with the Iron Age, heated and superheated basin recipes with the heater and the Blaze Burner, Create Big Cannons' deployer assembly with the Industrial Age, and the recipes of Applied Energistics 2's machines with the Information Age.
- **A home in the Stone and Bronze Age** (Supplementaries with FirmaSupplementaries, Every Compat, Handcrafted, Decorative Blocks Reborn, Macaw's Windows, Bridges and Doors, Moonlight Lib). Stone Age: palisades, beams, supports and seats, rope, log and rail bridges, timber frame and daub, jute rope and wall sconces that burn out like TerraFirmaCraft torches. Bronze Age: chairs, tables, benches, couches, cabinets, drawers and shelves, windows, shutters, blinds, curtain rods and doors in eight styles, in every TerraFirmaCraft and ArborFirmaCraft wood; signposts, notice boards, jars, sacks (heavy by TerraFirmaCraft rules), flags and pedestals. Fancy beds come with the Iron Age. Supplementaries' cannons, bombs, safes and flax are left out.
- **Age Lanterns and engraved bricks.** Every Age has its own lantern, made from a copper lamp and one material of that Age. The TerraFirmaCraft chisel (smooth mode) turns chiseled rock into an engraved brick with star, sun and moon, in all 21 rocks.
- **The Gathering.** In the Singularity Age the fellowship prays once at the complete shrine; with all nine relics on their plinths, Caelum gives them back for the last fusion. The relics never despawn. The quest "The Gathering" replaces "Nine Relics", and the HUD goal turns to "Beyond the Firmament".
- **A living shrine.** The shrine carries a thin beam day and night in the colour of the latest Age. It goes dark while a ring is broken and turns gold after the final boss. The heart hums quietly, the relics shed dust in their Age colour. Every player can turn the beam (`effects.idleBeam`) and the hum (`effects.ambientDrone`) off in `config/firmages-client.toml`.
- **Echo of the ceremony.** A player who was away at an Age-up sees the short ceremony a few seconds after joining, with one line "While you were away: ...".
- **The Origin.** The nine islands are rebuilt in consecrated stone, each with the sign item of its Age floating above it.
- **Firmament Night.** The first night after each new Age glows in that Age's colour, with a larger moon, half the monsters and 1.5 times the experience, and a line above the hot bar at dusk. Each Age gets its night once per world and never by chance.
- **All nine relics move:** the Hearthstone, the Sky Disc, the Steel Heart and the Pressure Core are animated now.
- **Quest book rewritten.** Every quest says first what to do, then what for. Titles without exclamation marks or mod names ("Stone Tools", "Blast Furnace", "Barrel", "A Jug of Water", which now comes before the pot). Each Age opens with "The X Age Dawns"; the goals name the forgotten rites (charge the Tesla Crown's capacitors with 1,000,000 FE, lay the Awakened Keystone back on its plinth before the Quantum Core), and one note, "When a Boss Will Not Come", replaces nine fallback paragraphs.
- **Texts in the pack's voice.** Locked items read "Something Unknown" and "Not of this Age"; the chat line at an Age change is gone; the welcome screen says "Open the Chronicle (K)". Breaking stone by hand in Dawn now says "Stone needs a pickaxe. The first one is cast in the Stone Age."
- **One fellowship.** A player who joins a server is put into the server's one party at the first login, without an invite, and plays in the same Age as everybody else. On a new server the first player founds the party "Firmament". A world where one player already reached an Age alone, outside a party, is left as it is. A host who wants separate parties sets `team.autoJoin = false` in `config/firmages-server.toml`.
- **The first night.** A Dawn side quest, "Before Dark", shows how to light a firepit and heat sticks into torches; the welcome screen says that nights are dark.
- **Patterns in the quest book.** "Unfired Hearth Idol" draws its knapping pattern and asks for two idols (one for the Shrine Heart, one for the Hearthstone). The new Stone Age note "Clay Patterns" draws the small vessel, ingot mold, pickaxe head mold, jug, pot, bowls and large vessel. "The Way Below" describes the sacrifice that opens the Nether.
- **A home in every early Age.** One note "A Home" per Age: Stone Age palisades, beams, timber frames and daub with their recipes, Bronze Age furniture and windows, Iron Age beds.
- **The Gathering waits for active players only.** A player who has not moved or acted for five minutes no longer holds back the finale (`origin.gatherIdleMinutes`), and the players at the altar see who is still missing.
- **The Cave Dweller keeps to the deep caves.** It only hunts players at or below y 0; copper and tin near the surface are mined in peace.
- **Age-ups reach every machine.** Right after an Age change the crafting table, the framing saw, the Imbuement Chamber, the distillation tower, coker and high-pressure refinery and AE2's in-world crafting work with the new recipes, without a restart.
- **Memory.** 8 GB is recommended for the game (6 GB minimum); the CurseForge zip carries this recommendation. A server needs 8 GB for up to about 4 players and 10 GB for 5 to 10; the server pack's README also explains world pregeneration with Chunky.
- **Updating a server:** a server that keeps its own `config/tfc-server.toml` adds `inferredMetals = ["ingots/iron", "double_ingots/iron"]` under `[general]` by hand (new in 0.8.1; `defaultconfigs` only fill a new file), and sets `permanentBeam = true` under `[shrine]` in `config/firmages-server.toml` for the shrine's idle beam.
- **Firmament Ages Core 0.9.0** with the Age swap, EMI, Ponder and GuideME by Age, the Gathering, the living shrine, the ceremony echo and the one fellowship. Network protocol 4: client and server both need 0.9.0.

## 0.8.1

- **Hands are not tools in Dawn.** Logs, raw and hardened rock, ores and sandstone cannot be broken by hand while the world is in Dawn: no crack, no progress, and a short hint in the action bar. A knapped stone axe (or stone hammer) fells trees as usual; dirt, gravel, sand, leaves, twigs and loose rocks still come by hand. From the Stone Age on TerraFirmaCraft's own rules apply again (slow, no drop by hand). A server can turn the rule off or extend it (`hardship.toolRuleUntilAge` in the Firmament Ages Core server config).
- **Knapping explained.** The Dawn quests "Just a Rock", "A Bundle of Sticks" and "Paleolithic!" walk through knapping step by step (two rocks in hand, right-click into the air, click away the cells of the 5x5 grid) with the axe and knife patterns drawn as grids; the new quest "Knapping Patterns" shows the hammer, shovel, hoe and javelin.
- **Create without iron in the Bronze Age.** The harvester, plough, minecart coupling, filter, item hatch, item vault, metal bracket, super glue, sticker, sticky piston (with TerraFirmaCraft glue), speedometer and stressometer (bronze sheet instead of a compass), chute, rope pulley, desk bell, weighted ejector, the valve handles, all toolboxes and the portable storage and fluid interfaces are made with bronze, as are Create Connected's freewheel and centrifugal clutches and item silo. Iron nuggets become bronze rods, the iron trapdoor a bronze trapdoor.
- **Early tools of other mods without iron.** Totemic carving knife (bronze ingot) and Sophisticated upgrade base (bronze) in the Bronze Age, the Astikor seed drill with a bronze sheet; the totem torch (TerraFirmaCraft torch), the liquid trash can (wooden bucket) and the FramedBlocks framing saw (copper) in the Stone Age.
- **TerraFirmaCraft machines in their real Age.** Blast furnace, bladed axles, barrel presses, kegs, chest and trapped-chest minecarts, the AFC tree tap and several Firmalife machines are listed in the Iron Age (vat, jarring station, picker, solar drier and sweeper in the Industrial Age), where they can actually be built.
- **Recipes repaired:** quartz from quartzite in the millstone; bleaching coloured alabaster bricks with lye; dyed placards back to white; the Bells & Whistles metro window with any glass.
- **Mana bar from the Arcane Age.** Ars Nouveau's mana and spell bars stay hidden until the Arcane Age.
- **Cleaner tooltips.** The TerraFirmaCraft weight and size line ("Light / Small") is gone from every item; a locked item shows only its name and the lock line; the Field Guide box ("?" and Ctrl) is off in new installs.
- **Field of view 90** in new installs.
- **Animated title screen:** the Shrine of every Age as a slowly turning 3D constellation, its rings lighting up in their Age colours. It replaces the slideshow.
- **Faster start, cleaner log.** TerraFirmaCraft Registry API no longer loads every class of every mod seven times at start; it only searches the mods that build on TerraFirmaCraft. Thousands of harmless error lines per start are gone (loot tables and recipes of mods that are not installed).
- **Firmament Ages Core 0.8.1** with the Dawn tool rule, the mana bar Age, the tooltip cleanup and the faster start. Client and server both need 0.8.1.

## 0.8.0

- **Shaders on by default.** Iris with Complementary Shaders – Reimagined (r5.9.3) starts switched on in a new install. MakeUp – Ultra Fast is included for weaker PCs; press **O** to switch packs or turn shaders off, and your choice stays. Shaders switch off by themselves on Ad Astra planets and orbits and in the Origin. Create contraptions render correctly under both packs (Colorwheel).
- **Enhanced Celestials 2** replaces Enhanced Celestials: the same blood, blue and harvest moons, with a shader bridge so a blood moon also tints the sky under Complementary. Joining stays silent.
- **Performance:** Dynamic FPS (lower frame rate in the background), AllTheLeaks (memory leak fixes) and BadOptimizations (small client-side render optimizations).
- **Beds from the Iron Age.** Vanilla beds open with the Iron Age; the TerraFirmaCraft thatch bed still sets your spawn but never skips the night. Half of the players online in bed are enough to skip the night.
- **Torches burn for 36 minutes** instead of 72, set by TerraFirmaCraft's `torchTicks` (new installs; an existing server sets `torchTicks` in `config/tfc-server.toml`).
- **Real darkness.** Nights and caves are dark (More Darkness, darker moon phases); a torch in your hand lights your way (LambDynamicLights). With shaders, the shader settings shipped with the pack keep nights and caves dark as well.
- **No coordinates until the Bronze Age.** The debug screen (F3) hides your position until the Bronze Age; the gravestone no longer comes with an obituary that names its coordinates.
- **Blood moons from the Iron Age.** No blood moon rises while nobody has reached the Iron Age mobs; after that they come back right away.
- **Cave Dweller until the Iron Age.** Cave Dweller ReEvolved hunts the caves of the early Ages, one at a time and only in the Overworld. From the Iron Age on it is gone, its sounds included.
- **Block info (Jade) from the Bronze Age,** together with the recipe viewer (new worlds; an existing world keeps its Firmament Ages Core config).
- **The quest book is a window.** It opens as a centred window of 75 percent of the game window (client setting `ui.questWindowPercent`, 0 for fullscreen) and no longer cuts off the quest view; the gaps between the parts of a quest are tighter.
- **Constellation title screen:** the night sky with the Shrine of every Age drawn as a star chart.
- **Removed:** Ping Wheel, Crash Assistant and TFCGenViewer.
- **One item per material.** Amethyst, quartz, saltpeter, cast iron, sterling silver, wood dust, amethyst dust and andesite sheets now show one item each in the recipe viewer, and recipes that made a second version give that one item (Almost Unified). Nether quartz opens the Twilight Forest portal and goes into the Frontier Sigil.
- **Mystical Agriculture quest:** the soulstone task asks you to find a body of soulstone instead of collecting eight; mined without Silk Touch, soulstone drops as cobble.
- **Quest texts wrap by paragraph.** Each paragraph of a quest description is one block that fills the width of the quest window, without line breaks in the middle of a sentence.
- **Firmament Ages Core 0.8.0** with the Jade default, the Cave Dweller gate and the quest window.

## 0.7.2

- **Title and pause menus fixed.** The title screen shows only Singleplayer, Multiplayer, Options and Quit; the Language and Accessibility buttons and Create's config button are gone (both settings stay under Options), and the version line is no longer cut off. The pause menu keeps Back to Game, Options and Disconnect, restyled and centred; Advancements, Statistics, feedback, bug report, server links and Open to LAN are hidden.
- **TerraFirmaCraft inventory tabs open with the Ages.** The calendar tab appears with the Stone Age and the climate tab with the Iron Age; the nutrition tab and the Field Guide tab stay hidden. The remaining tabs move up without gaps, and the server refuses a locked tab. Each tab can be set per world in the Firmament Ages Core config (`ui.calendarTabFromAge`, `ui.climateTabFromAge`, `ui.nutritionTabFromAge`, `ui.fieldGuideTabFromAge`).
- **Simple Voice Chat removed.** Its keys are free again (Alt+M among them).
- **Mystical Agriculture ores come from TerraFirmaCraft veins.** Inferium ore (every rock type), prosperity ore (igneous and metamorphic rock, deep) and soulstone with soulium ore (basalt only) generate as veins in new chunks. Until the Information Age they look like plain rock, drop plain rock and do not show up when prospecting. The Information Age side quest for Mystical Agriculture now asks for essence, shards and soulstone.
- **Tighter quest text.** Paragraphs in the quest view sit closer together (14 instead of 18 pixels; `ui.questParagraphGap` in the client config of Firmament Ages Core).
- **Firmament Ages Core 0.7.2** with the menu buttons, the TerraFirmaCraft tabs and the quest text spacing.

## 0.7.1

- **The recipe viewer comes with the Bronze Age.** The block info overlay (Jade) still appears with the Stone Age, the recipe viewer (EMI) now with the Bronze Age. This is the default for new worlds; an existing world keeps the values in its Firmament Ages Core config (`ui.emiFromAge`, `ui.jadeFromAge`).
- **Two key conflicts resolved for new installs:** Tab stays the player list and Y the Cataclysm ability; the Physics Staff rotate mode (Create Simulated) moves to Comma and the Cataclysm chestplate ability to Apostrophe.
- **MekaSuit and Meka-Tool modules can be made.** The module base asked for bronze nuggets, which nothing in this pack makes; it now takes TerraFirmaCraft bronze ingots, so every module opens with its Age.
- **One steel slab.** The TerraFirmaCraft steel slab is disabled; the Immersive Engineering steel slab (part of the coker unit) remains.
- **No Immersive Engineering chunk loader.** The Resonanz Observer is disabled; chunk loading is FTB Chunks' job (force-loaded chunks from the Industrial Age).
- **Firmament Ages Core 0.7.1** with the new defaults.

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
