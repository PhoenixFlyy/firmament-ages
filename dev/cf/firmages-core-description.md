# CurseForge mod project: Firmament Ages Core (firmages-core)

Text and settings for the CurseForge **Mod** project of firmages-core. The description (between the two rules) is English and goes into the project's description field as is. The upload checklist below it is for Felix.

| Field | Value |
|---|---|
| Project name | Firmament Ages Core |
| Summary (max. 255 characters) | The core mod of the Firmament Ages modpack: shared Ages for one team, a machine-recipe gate, a miner filter, the Shrine of Caelum with its ceremony, Draconic reactor refuelling and The Origin. |
| Class | Mods |
| Main category | Miscellaneous |
| Additional categories | Server Utility, Magic (the Shrine), API and Library |
| License | MIT License (`mod/firmages-core/LICENSE`) |
| Source | optional: link the GitHub repository once it is public; leave empty while it is private |
| Logo | `dev/cf/avatar-400.png` (400x400, CurseForge avatar size) |
| Game version / loader | Minecraft 1.21.1, NeoForge, Java 21 |
| Mod id / jar | `firmages`, `firmages-core-0.6.1.jar` |

---

## Firmament Ages Core

**Firmament Ages Core** is the companion mod of the [Firmament Ages](https://www.curseforge.com/minecraft/modpacks/firmament-ages) modpack (Minecraft 1.21.1, NeoForge). It turns a list of progression stages into Ages that a whole team reaches together, makes every machine respect them, and gives the pack its centrepiece: the Shrine of Caelum.

> This mod is made for Firmament Ages. Its Ages, tags, recipes and quests come from the pack's data. In another pack it starts, but it does nothing useful until you provide the same kind of data.

### What it does

- **Shared Ages.** One server-wide Age state (Dawn, then Stone to Singularity Age), mirrored from ProgressiveStages and saved with the world. The pack has one team, so the team's stages are the server's stages.
- **Machine-recipe gate.** No machine in any mod makes an item of a locked Age. The mod filters the recipe list when it loads, with readers for Create, Immersive Engineering, Mekanism, Occultism, Ars Nouveau, TerraFirmaCraft and Draconic Evolution plus a generic JSON scan. One datapack reload per new Age brings the next Age's recipes back. `/firmages recipes why <id>` explains every decision.
- **Miner filter.** The Immersive Engineering excavator, fake-player block breakers and Mekanism's Digital Miner only yield ores of unlocked Ages. Prospecting tools only report unlocked ores.
- **The Shrine.** A Shrine Heart and one Offering Plinth per Age. Each Age adds a ring to the shrine (a Modonomicon multiblock, shown as a ghost preview). Offer the Age's signature item, fulfil the ring's rite (pray at night, keep a lamp lit, charge the capacitors, pray together), then pray at the heart. Caelum answers with a ceremony: music fades, a beam of light, a tinted sky, a title, and the next Age for the whole team. Every awakened ring adds a blessing around the shrine, starting with a circle where no monsters spawn. The offerings stay on the shrine as relics; the Arcane Keystone can be borrowed for the Quantum Age and must be brought back. An accepted ring is consecrated: its blocks become one unbreakable sky-marble family with the Age's colour accent, and a short maintenance mode at the heart lets the team rebuild it.
- **Draconic reactor refuelling.** A Reactor Controller block refuels a Draconic Evolution reactor through pipes and signals READY by redstone. It never restarts the reactor on its own.
- **The Origin.** The finale dimension of the pack: a fixed Stargate Journey address, an arena, its own spawn list and travel lock, and the end-boss trigger. The fight itself is a KubeJS script of the pack.
- **Tools for pack makers.** `/firmages ages`, `/firmages recipes audit|why|locked`, `/firmages dump registry`, `/firmages shrine status`, `/firmages selftest`, a KubeJS binding `FirmAges`, Jade tooltips for the shrine. Shrine rings, tiers, offerings, rites and blessings are data files (`data/firmages/firmages_shrine/`, `data/firmages/modonomicon/multiblocks/`) and can be replaced by a datapack.

### Dependencies

Required:
- [NeoForge](https://neoforged.net) 21.1.252 or newer for Minecraft 1.21.1
- [ProgressiveStages](https://www.curseforge.com/minecraft/mc-mods/progressivestages) 3.0.5 or newer
- [Modonomicon](https://www.curseforge.com/minecraft/mc-mods/modonomicon)

Optional (the mod hooks into them when they are present):
- KubeJS (binding `FirmAges`), FTB Teams
- Create, Immersive Engineering, Mekanism, Occultism, Ars Nouveau, TerraFirmaCraft, Applied Energistics 2 (compat hooks)
- Draconic Evolution with Brandon's Core and CodeChicken Lib (reactor controller)
- Stargate Journey (The Origin's gate)
- Jade (shrine tooltips), GeckoLib

### Configuration

`config/firmages-server.toml` (gate, miner filter, prospecting, shrine, blessings, ceremony timing, debug commands) and `config/firmages-client.toml` (ceremony visuals and music).

### License

MIT. The textures are generated pixel art made for this mod.

### Changelog

**0.6.1** (first CurseForge release)
- Warm-up reload: a dedicated server runs one Age reload right after it starts, while nobody is online, so the first reload players wait for is not the slow cold one (`gate.warmupReload`, `gate.warmupDelayTicks`).
- Joining a world, changing dimension or respawning sends the ProgressiveStages lock sync once instead of twice.

**0.6.0**
- Consecration: when Caelum accepts a ring, its blocks turn into one consecrated family (sky marble with one colour accent per Age), from the heart outward. Consecrated blocks are unbreakable; sneak and punch the heart with an empty hand for 60 s of maintenance, then they break and drop their original material. Rings built before the update are consecrated when the shrine next checks them.
- Textures v2: redrawn items, animated signature items and altar top.
- MIT licence.

**0.5.0**
- Shrine: rings 0 to 8 with their rites and blessings (sanctuary, walking speed, health, XP, mining speed, reach, fall damage, luck), the Keystone lending of the Quantum Age, Jade tooltips, generated block textures.
- The Origin: own travel lock, `mob_9` spawn list, hardened boss trigger.
- Reactor Controller for the Draconic reactor (0.4.0), The Origin and the end-boss scaffolding (0.4.0).
- Machine-recipe gate with late pass, miner filter, stage-aware prospecting (0.2.x), core Age state and reload orchestration (0.1.x).

---

## Upload checklist (Felix)

1. **Build state:** upload `mods/firmages-core-0.6.1.jar`; its `neoforge.mods.toml` says `license="MIT"`, the same licence as the project. CurseForge moderators compare them.
2. **Project:** curseforge.com → *Create Project* → Minecraft → *Mods*. Fields from the table above, description from the section between the rules, avatar `dev/cf/avatar-400.png`. The modpack link in the first paragraph assumes the slug `firmament-ages`; fix it once the modpack project exists (or drop the link for the first submission).
3. **File:** *Upload File* → the jar; *Release type* **Beta** (the pack is in development, and two required mods of the pack are beta); game versions **1.21.1**, loader **NeoForge**, Java 21; changelog: the 0.6.1 and 0.6.0 entries above.
4. **Relations** (on the file): *Required Dependency* ProgressiveStages and Modonomicon; *Optional Dependency* KubeJS, Create, Immersive Engineering, Mekanism, Draconic Evolution, Stargate Journey, Jade.
5. **Wait for approval** (usually hours to a few days). Note the **project id** and the **file id** shown on the file page.
6. **Pack:** in the repo root `tools\packwiz.exe curseforge add --addon-id <project id> --file-id <file id>`, delete `mods/firmages-core-0.6.1.jar`, `tools\packwiz.exe refresh`, commit. From then on the pack lists the mod in the CurseForge manifest instead of carrying the jar in `overrides/`.
7. **Every later version:** upload the new jar to this project first, then update the pack's metafile (`packwiz curseforge add` with the new file id, or `packwiz update firmages-core`).
