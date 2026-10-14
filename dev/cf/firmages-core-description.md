# Firmament Ages Core

**Firmament Ages Core** is the companion mod of the [Firmament Ages](https://www.curseforge.com/minecraft/modpacks/firmament-ages) modpack (Minecraft 1.21.1, NeoForge). It turns a list of progression stages into Ages that a whole team reaches together, makes every machine respect them, and gives the pack its centrepiece: the Shrine of Caelum.

> This mod is made for Firmament Ages. Its Ages, tags, recipes and quests come from the pack's data. In another pack it starts and does nothing until you provide the same kind of data (stage names, the `firmages:age_items/<stage>` tags and the shrine files). It is published here so the pack can list it as a dependency.

## What it does

- **Shared Ages.** One server-wide Age state (Dawn, then Stone to Singularity Age), mirrored from ProgressiveStages and saved with the world. The pack has one team, so the team's stages are the server's stages. A player who joins later is in the same Age as everybody else.
- **Global recipe gate.** No machine in any mod makes an item of a locked Age. The mod filters the recipe list when it loads, with typed readers for Create, Immersive Engineering, Mekanism, Occultism, Ars Nouveau, TerraFirmaCraft and Draconic Evolution plus a generic JSON scan for everything else. One datapack reload per new Age brings the next Age's recipes back; a dedicated server runs a warm-up reload right after it starts so the first one players wait for is not the slow cold one. `/firmages recipes why <id>` explains every decision.
- **Miner and prospecting filters.** The Immersive Engineering excavator, fake-player block breakers and Mekanism's Digital Miner only yield ores of unlocked Ages. Prospecting tools only report unlocked ores.
- **The Shrine.** A Shrine Heart and one Offering Plinth per Age. Each Age adds a ring to the shrine (a Modonomicon multiblock, shown as a ghost preview). Offer the Age's signature item, fulfil the ring's rite (pray at night, keep a lamp lit, charge the capacitors, pray together), then pray at the heart. Caelum answers with a ceremony: music fades, a beam of light, a tinted sky, a title, and the next Age for the whole team. Every awakened ring adds a blessing around the shrine, starting with a circle where no monsters spawn. The offerings stay on the shrine as relics; the Arcane Keystone can be borrowed for the Quantum Age and must be brought back; from the last Age on the relics can be taken for the finale.
- **Consecration and maintenance mode.** An accepted ring is consecrated: its blocks become unbreakable consecrated stone with the Age's colour accent, from the heart outward. Sneak and punch the heart with an empty hand for 60 seconds of maintenance mode; then the blocks break and drop their original material, and the ring can be rebuilt.
- **Reactor Controller.** A block that refuels a Draconic Evolution reactor through pipes or from an adjacent chest and signals READY by redstone. It never restarts the reactor on its own.
- **The Origin.** The finale dimension of the pack: a fixed Stargate Journey address, an arena, its own spawn list and travel lock, and the end-boss trigger. The fight itself is a KubeJS script of the pack.
- **Immersion.** Nothing of the pack's machinery shows before its time: join messages and popups of other mods, advancement toasts and the advancement screen (the quest book opens instead) are silenced, the Mods and Advancements buttons are hidden, and EMI and Jade stay off until the server's configured Age (`ui.emiFromAge`, `ui.jadeFromAge`). A small HUD line names the current Age and its offering (fades out, hold H to show), and every new player gets a one-time welcome. With TerraFirmaCraft, thirst is off and nutrition can only raise health above the starting value, never lower it.
- **Tools for pack makers.** `/firmages ages`, `/firmages recipes audit|why|locked`, `/firmages dump registry`, `/firmages shrine status`, `/firmages selftest`, `/firmages ceremony preview`, headless debug players for server-side tests, a KubeJS binding `FirmAges`, Jade tooltips for the shrine. Shrine rings, tiers, offerings, rites and blessings are data files (`data/firmages/firmages_shrine/`, `data/firmages/modonomicon/multiblocks/`) and can be replaced by a datapack.

## Dependencies

Required:

- [NeoForge](https://neoforged.net) 21.1.252 or newer for Minecraft 1.21.1, Java 21
- [ProgressiveStages](https://www.curseforge.com/minecraft/mc-mods/progressivestages) 3.0.5 or newer
- [Modonomicon](https://www.curseforge.com/minecraft/mc-mods/modonomicon)

Optional (the mod hooks into them when they are present):

- KubeJS (binding `FirmAges`), FTB Teams (one-team guard)
- Create, Immersive Engineering, Mekanism, Occultism, Ars Nouveau, TerraFirmaCraft, Applied Energistics 2 (recipe readers and miner hooks)
- Draconic Evolution with Brandon's Core and CodeChicken Lib (reactor controller)
- Stargate Journey (The Origin's gate)
- Jade (shrine tooltips)
- FTB Quests (quest window and text spacing)
- Cave Dweller ReEvolved (Cave Dweller until an Age)
- TerraFirmaCraft Registry API (faster start: narrower enum scan)

## Configuration

`config/firmages-server.toml` (gate, warm-up reload, stage sync batching, miner filter, prospecting, shrine, blessings, ceremony timing, immersion: recipe viewer and block info Ages, welcome, TerraFirmaCraft thirst, nutrition and inventory tabs, Cave Dweller Age, Dawn tool rule, start-up scan scope, debug commands) and `config/firmages-client.toml` (ceremony visuals and music, HUD mode and fade, blocked toasts and screens, chat blocklist, hidden menu buttons, TerraFirmaCraft displays and weight/size line, Ars Nouveau HUD Age, locked-item tooltips, quest window size and paragraph gap).

## Source and license

MIT License. Source: the `mod/firmages-core` folder of the pack repository, [github.com/PhoenixFlyy/firmament-ages](https://github.com/PhoenixFlyy/firmament-ages/tree/main/mod/firmages-core). Issues go to the [pack's issue tracker](https://github.com/PhoenixFlyy/firmament-ages/issues). The textures are generated pixel art made for this mod.

## Changelog

**0.9.1**

- Team carry-over: when a player enters an FTB Teams party (founding, joining, an accepted invite), one tick later the party gets every Age of the world's Age record it lacks, plus `finale_won` after the final boss, through ProgressiveStages' grant path; no ceremony, swap or Firmament Night. A party member's login repeats it. `team.autoJoin` founds "Firmament" also on a world where one player already holds Ages: that player founds it (FTB Quests moves the founder's quest book into the party); while that player is offline a newcomer is told who holds the Ages (`firmages.team.waiting`). Dormant player teams and disbanded parties no longer count for the one-team guard or the boot union.
- EMI for operators (permission level 2) and creative players in every Age, recipe screen included.
- EMI item lists: a recipe input that lists several items shows only the members of the player's Ages (mixin on `ListEmiIngredient.getEmiStacks`; a fully locked list still drops the recipe).
- TFCRegistryAPI scan: inside the scanned TerraFirmaCraft mods only enum classes are loaded (the class file's flags are read first), which removes about 112 DISTXFORM, mixin and FATAL lines per server boot.
- Self-test: `recipe_caches_fresh` compares each mod's recipe cache with what the mod's own code rebuilds from the live recipes; new case `crafting_table_follows_ages`; `/firmages recipes craft <id>`.
- Network protocol version 4, unchanged.

**0.9.0**

- Age swap: an Age change no longer reloads the datapacks. The gate classifies every recipe once per load; a swap filters that list again for the new Ages (`RecipeManager#replaceRecipes`), rebinds `tfc:prospectable`, `precisionprospecting:prospectable_mineral` and `mekanism:miner_blacklist`, flushes the recipe caches of Mekanism, Create, TerraFirmaCraft, Immersive Engineering and Mystical Agriculture, and sends only the recipe packet and recipe book. `/reload`, `/firmages reload` and the boot reconcile still load everything. `gate.ageSwap` (server, default on); a swap that cannot run falls back to the full reload. KubeJS gets `FirmAges.stationAge`, `requireAge` and `untilAge`; the Create basin heat levels are station locks of the gate.
- EMI by Age: an EMI plugin disables every stack of a locked Age (items and fluids by `firmages:age_items` and `age_fluids`, other stack types by mod, books, enchantments and potions that name a locked mod) and removes the categories and EMI Loot tables of locked mods. One EMI reload per Age change (`firmages:age_status` `reloadPending`). EMI's recipe and recipe tree screens stay closed while the recipe viewer is locked. The client command `/firmages_client emi_audit` writes the audit of the current Age to `logs/`.
- Ponder hides locked items from its index and tag screens; `/ponder` below permission level 2 only answers how to ponder an item. GuideME's client command `/guidemec` is removed.
- The Gathering (Singularity Age): one prayer at the intact shrine with all nine relics present releases them; they never despawn. The heart shows `awakened=10`, the HUD goal changes. Living shrine: an idle beam in the latest Age colour (`shrine.permanentBeam`, server, default on in new worlds; `effects.idleBeam` client), a hum near the heart (`effects.ambientDrone`) and relic dust. Ceremony echo: a player who missed an Age-up gets one short ceremony after login (player attachment `firmages:seen_age`). The Origin islands are rebuilt (arena version 2) with floating sign items.
- Operator commands: `/firmages origin tp <players> force`, `/firmages origin gathering`, `/firmages origin fight`.
- Network protocol version 4 (`firmages:age_status` gained `reloadPending` and `gathered`, `firmages:shrine_sync` the living-shrine fields): client and server need 0.9.0.

**0.8.1**

- Hand-breaking rule: `hardship.toolRuleUntilAge` (server, default `age_0`, so the rule holds in Dawn only; an Age id, `never` or `dawn` turn it off). Below that Age, blocks in `#firmages:needs_tool_early` (logs, raw and hardened rock, ores, sandstone) do not break without a correct tool for drops: break speed 0 on client and server, the break is cancelled on the server, and an action-bar hint shows at most once per minute. Creative, spectator and machine fake players are exempt. The client takes the rule from `firmages:age_status`.
- Ars Nouveau's mana and spell HUD stay hidden below `ui.manaHudFromAge` (client, default `age_3`).
- Locked items: the tooltip keeps only the name and ProgressiveStages' lock lines (`ui.trimLockedTooltips`, client, default on).
- TerraFirmaCraft's weight and size tooltip line is removed (`tfc.hideSizeWeight`, client, default on); TFC 4.2.11 has no setting for it.
- Faster start with TerraFirmaCraft Registry API: its seven enum scans only read mod files that are or depend on `tfc` or `tfcregistryapi` instead of every class of every mod (`perf.limitTfcRegistryApiScan`, server config, default on, read once at game start).
- Network protocol version 3 (`firmages:age_status` grew): client and server need 0.8.1.

**0.8.0**

- `ui.jadeFromAge` defaults to the Bronze Age (`age_1`), the same Age as the recipe viewer. Existing configs keep their values.
- Cave Dweller ReEvolved until an Age: `hardship.caveDwellerUntilAge` (server, default `age_2`, the Iron Age; an Age id or `never`). From that Age on the server refuses the dweller when it would join a level (its own spawns, `/summon`, dwellers saved in chunks) and removes loaded ones after every Age change; `firmages:age_status` carries whether it still hunts, and the client then drops every `cave_dweller` sound (its cave noises come on a timer even without a dweller).
- Quest book as a window (FTB Quests): `ui.questWindowPercent` (client, default 75, 0 = fullscreen) sizes the quest screen to that share of the game window, centred, with the chapter list, the map and every pop-up inside it. Pop-ups are clamped in screen coordinates (FTB Library put a screen-sized quest view 10 pixels off the edge), and the map centre and the chapter list work on a window. The gaps between title, tasks, separator and text of the quest view shrink with `ui.questParagraphGap`.
- Enhanced Celestials 2 has no join screen; `ui.blockedScreens` ships empty.
- Network protocol version 2 (`firmages:age_status` grew in 0.7.2 and 0.8.0): client and server need 0.8.0.

**0.7.2**

- TerraFirmaCraft inventory tabs by Age: `ui.calendarTabFromAge` (default Stone Age), `ui.climateTabFromAge` (Iron Age), `ui.nutritionTabFromAge` and `ui.fieldGuideTabFromAge` (both `never`). The server sends the open tabs with `firmages:age_status` (one more byte; client and server need 0.7.2), the client removes locked tab buttons and closes the gaps, and a server mixin refuses TFC's tab packet for a locked tab.
- The client setting `tfc.hideNutrition` now only hides the nutrition lines in food tooltips; the nutrition tab follows the server key.
- Title screen: the Language and Accessibility buttons are hidden (`ui.hideMenuButtons`), FancyMenu 3.9.14 no longer finds them on 1.21.1.
- FTB Quests: a blank description line becomes a gap of `ui.questParagraphGap` pixels (client, default 4, 0 to 9) instead of a full text line, and the gap after the subtitle takes the same value.

**0.7.1**

- New defaults: `ui.emiFromAge` = Bronze Age (`age_1`), `ui.jadeFromAge` = Stone Age (`age_0`). Existing configs keep their values.

**0.7.0**

- Immersion: silent joins (Enhanced Celestials screen, Ars Nouveau, Train Utilities and Corgilib notices, a client chat blocklist as safety net), no advancement, recipe or tutorial toasts, the advancement screen redirected to FTB Quests, Mods and Advancements buttons hidden, mod-name tooltip lines removed.
- EMI and Jade follow the world's Age: the server sends `firmages:age_status` on login and after every Age change; config `ui.emiFromAge`, `ui.jadeFromAge` (default Stone Age).
- HUD Age indicator (top left, fades after 6 s, hold H to show; `ui.hudMode`, `ui.hudFadeSeconds`) and a one-time welcome title and screen per player (`ui.welcome`, `/firmages welcome reset|show`).
- TerraFirmaCraft: thirst disabled (`tfc.disableThirst`), nutrition never lowers health below TFC's starting modifier (`tfc.noNutritionMalus`); thirst bar, nutrition tab and nutrition tooltip lines hidden on the client.

**0.6.2**

- Granting an Age no longer stalls the server once per stage change and player. ProgressiveStages resynced every team member with a full lock sync for each stage the grant script added or removed; the mod now collects those syncs and sends one lock sync and one stage sync per player at the end of the tick (`sync.coalesce`, on by default).
- The headless debug players log the ProgressiveStages lock and stage syncs they receive, and GameTests can read the order of their packets.

**0.6.1**

- Warm-up reload: a dedicated server runs one Age reload right after it starts, while nobody is online, so the first reload players wait for is not the slow cold one (`gate.warmupReload`, `gate.warmupDelayTicks`).
- Joining a world, changing dimension or respawning sends the ProgressiveStages lock sync once instead of twice.
- Builds against ProgressiveStages 3.0.6, Stargate Journey 0.6.51 and Occultism 1.224.5; the 3.0.5 lower bound for ProgressiveStages stays.

**0.6.0**

- Consecration: when Caelum accepts a ring, its blocks turn into one consecrated family (sky marble with one colour accent per Age), from the heart outward. Consecrated blocks are unbreakable; sneak and punch the heart with an empty hand for 60 s of maintenance, then they break and drop their original material. Rings built before the update are consecrated when the shrine next checks them.
- Textures v2: redrawn items, animated signature items and altar top, the consecrated block family.
- MIT License.

**0.5.0**

- Keystone lending: the Arcane Keystone can be borrowed from its plinth in the Quantum Age and must rest on it again before the next ceremony.
- Blessing effects for rings 0 to 8: sanctuary, walking speed, health, XP, mining speed, reach, fall damage, luck.
- The energy rite of the Electric ring (charged IE capacitors), Jade tooltips for the heart and the plinths.
- The Origin: own travel lock from the Singularity Age, its own spawn list, hardened boss trigger.
- Generated block textures.

**0.4.0**

- Reactor Controller for the Draconic reactor (refuel through pipes, READY by redstone, no auto-restart) and the fuel-port item capability.
- The Origin: the dimension, the arena, the fixed Stargate Journey address and the return gate, the end-boss scaffolding.

**0.3.0 to 0.3.3**

- The Shrine: Shrine Heart, Offering Plinths, rings 0 to 2 as Modonomicon multiblocks with ghost preview, the state machine (offering, rite, prayer), the Age ceremony with beam, sky tint, titles and music fade (0.3.0).
- Rings 3 to 8 and one blessing per Age as data (0.3.1).
- The duplicate ProgressiveStages lock sync inside a reload is skipped (0.3.2); 0.3.3 is the first build shipped with both.

**0.2.0 and 0.2.1**

- The machine-recipe Age gate with typed readers for Create, Immersive Engineering, Mekanism and Occultism plus the JSON output scan, `/firmages recipes audit|why|locked`, cache flush for the Occultism mineshaft and the Ars apparatus (0.2.0).
- The IE excavator filter, the fake-player ore guard, stage-aware prospecting through the KubeJS binding (0.2.0).
- Late pass of the gate for recipes other mods inject after the recipe manager loads (0.2.1).

**0.1.0 and 0.1.1**

- Age state saved with the world and mirrored to `<world>/firmages/ages.json`, the Age index from the `firmages:age_items|age_blocks|age_fluids/<age>` tags, the coalescing reload scheduler, the commands `/firmages ages|dump registry|reload|selftest`, the KubeJS binding `FirmAges`, the server and client configs.
- Boot fallback read from `config/` (0.1.1).
