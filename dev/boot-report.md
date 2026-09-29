# Test-server boot report

Date: 2026-09-30. Pack 0.1.0, Minecraft 1.21.1, NeoForge 21.1.252, Temurin 21.0.12.1 (`tools/jdk-21`), `-Xms6G -Xmx8G`.
Server folder: `test-server/` (git-ignored), installed with `neoforge-21.1.252-installer.jar --installServer`,
synced with `packwiz-installer-bootstrap.jar -g -s server http://localhost:8080/pack.toml`.
Runner: `python dev/run_server.py` (see its docstring for flags).

## Result

The server reaches `Done` with no mod-loading errors, no KubeJS script errors and no broken tags.
Five boots were used.

| Boot | Result | Fix that followed |
|---|---|---|
| 1 | FML: missing `tfcregistryapi`, `astikorcartsredux`, `blockrunner` | Added TFC Registry API 1.2, AstikorCarts Redux 1.2.4, Block Runner v21.1.2 (plus Puzzles Lib 21.1.62). These deps are declared only in the jars, not on Modrinth, so `gen_packwiz.py` missed them. It now lists them in `JAR_DEPS` |
| 2 | Done; KubeJS `bronze_age.js#82: redeclaration of var querns` | Rhino rejects `const` declared directly inside a `try` block. Fixed there and in `grants.js` `reconcile()` |
| 3 | Done; clean KubeJS; `progressivestages validate` 28/28 | Log audit found vanilla discarding whole tags (next row) |
| 4 | Done; 0 `Couldn't load tag` lines | `kubejs/server_scripts/tags/broken_mod_tags.js` (see below) |
| 5 | Done on a fresh world (`--wipe-world`) | none |

## Numbers (boot 5, fresh world)

- Wall clock from JVM start to `Done`: about 110 s, including the TFC spawn area (3.9 s). The server itself reports `Done (12.8s)`. A boot on an existing world takes 80 to 95 s.
- Mods: 206 mod files with 201 mods (177 jars in `mods/` plus jar-in-jar). Fifteen client-only jars were skipped by packwiz (wrong side).
- 35,213 recipes and 12,249 advancements loaded. KubeJS: `Added 245 recipes, removed 180 recipes, modified 17 recipes, with 0 failed recipes`.
- ProgressiveStages: `Loaded 28 stage definitions`; `progressivestages validate` gives `SUMMARY: 28/28 stage files valid`. The stage tree matches `dawn → age_0 → … → age_9 → finale_won`, with `ftbchunks_mapping` under `age_2`.
- `progressivestages ftb status`: Provider Registered YES, Compat Active YES, Previous Provider Stored YES (the KubeJS Stages provider from FTB XMod Compat, restored on shutdown).
- `neoforge tps` while idle: 20 TPS in every dimension. The overworld runs at about 2.3 ms/tick; the overall total is about 13 ms/tick across 25 dimensions (Ad Astra and Stargate Journey add most of them).
- The world type is `tfc:overworld`: it is the preset id in TFC 4.2.11 (`data/tfc/worldgen/world_preset/overworld.json`), and `level.dat` references it.

## Broken mod data fixed in the pack

When a required tag entry is missing, vanilla drops that tag. It also drops every tag that includes the dropped one.

- **MekaTFC 0.1.0** tags `tfc:metal/ingot/lead`, `tfc:metal/sheet/lead`, `tfc:metal/block/lead`, `tfc:powder/galena`, `tfc:ore/{poor,normal,rich,small}_galena[/<rock>]` and the cupronickel ingot and block. TFC 4.2.11 has none of these ids. In this pack, lead comes from TFC + IE Crossover.
  - The pack lost `c:ores`, `c:ingots`, `c:dusts`, `c:sheets`, `c:storage_blocks` and `c:raw_materials`.
  - Through `#c:ores` it also lost `tfc:prospectable`, `tfc:can_collapse`, `tfc:can_start_collapse`, `tfc:can_trigger_collapse`, `tfc:monster_spawns_on` and `tfc:powderkeg_breaking_blocks`, plus the Mekanism, IE, Theurgy, Twilight Forest and Mowzie tags built on them.
  - The Mekanism lead processing recipes were marked incomplete.
- **WoodenCog 1.2.19** tags `woodencog:unfired_fireclay_crucible` and `woodencog:fireclay_crucible`, which it does not register. This broke `tfc:unfired_pottery`.

`broken_mod_tags.js` removes exactly these entries. Delete it when the mods ship fixed data. To check, look for `Couldn't load tag` in the boot log.

## Warnings worth knowing (not fixed, upstream noise)

- **About 10,200 `RuntimeDistCleaner` errors** ("Attempted to load class net/minecraft/client/... for invalid dist DEDICATED_SERVER"). Common-side mod code touches client classes, and the loads fail harmlessly. Azimuth 1.4.9 (latest) also logs 1,603 FATAL `SafeBlockEntityRendererMixin` failures against Create's client renderer. Bits 'n' Bobs, WoodenCog (JEI) and Simulated log a few client-only mixin failures of the same kind. None of this stops the boot. Look at it again only if a client join crashes.
- **480 loot-table parse errors** for blocks that are only registered with absent compat mods: Railways (BYG tracks), Design n' Decor, Create Connected, Create Encased, More Immersive Wires, ExtendedAE (EMC interface).
- **Recipe parse errors** in third-party data:
  - WoodenCog: welding, milling quartz and loam-brick cutting.
  - TFC Regrowing Forests: its `dttfc` recipes (Dynamic Trees TFC is not in the pack), plus fig and cacao, which TFC 4.2.11 lacks.
  - KubeJS falls back to vanilla parsing for 85 third-party recipes: 47 from Create Framed, 18 from Create Diesel Generators (`fluid_tag` and `fluid_stack` ingredient types), 13 from WoodenCog, 4 from MekaTFC (osmium dissolution) and 3 others.
  - These recipes may not work in game. Check them in the client PoC if they matter.
- **Sable:** "Failed to apply tag physics properties" for `copycats:copycat_catwalk` and `sable:super_heavy`.
- **Config corrections:** NeoForge rewrites `config/progressivestages/progressivestages.toml` to add default keys. All pack keys survive with their values (checked by diff). `config/EvolvedMekanism/general.toml` is corrected the same way. Both files are rewritten again on every packwiz sync, which is harmless.
- **defaultconfigs:** NeoForge 21.1 copies `defaultconfigs/*` to `config/` the first time a config is created. It does not copy them to `world/serverconfig/`. `config/tfc-server.toml`, `config/immersiveengineering-server.toml` and `config/Mekanism/world.toml` carry the pack values. PoC item A11 in `poc-checklist.md` names `world/serverconfig/`; check `config/` instead. On the live server, a config file that already exists is never replaced by `defaultconfigs`.
- **Console commands:** `kubejs errors` needs a player, so it cannot run from the console. Use `logs/kubejs/*.log` instead; `run_server.py` summarises them.

## Not covered here

- No client has joined yet, so client-side crashes and the client-only mods are untested. See `dev/dev-client.md` and the C section of `poc-checklist.md`.
- The in-game checks A6 to A9 and A11 to A14 from `poc-checklist.md` have not been run.
