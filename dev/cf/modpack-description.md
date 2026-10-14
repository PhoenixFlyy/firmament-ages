# Firmament Ages

**A co-op progression pack for Minecraft 1.21.1 (NeoForge): from knapping flint in a TerraFirmaCraft world to a Draconic reactor, a Stargate and a final boss beyond the sky. Ten Ages, one team, one shrine.**

Firmament Ages is built for a small group of friends who play together in one team. Progress is shared: when the team reaches a new Age, everybody does. Every Age has one clear goal, a signature item, and you can see the final goal from the first minute.

## The Ages

You start in **Dawn**, a cave-dweller prologue: no pickaxe, no fire, only stones, sticks and knapping. Then ten Ages follow, each opening new mods, machines, ores and dimensions:

| Age | What opens | Signature item |
|---|---|---|
| Stone Age | Pottery, pit kilns, copper casting, ArborFirmaCraft | Hearthstone |
| Bronze Age | TFC anvils, three bronzes, Create (Andesite tier), Sophisticated Backpacks | Sky Disc |
| Iron Age | Bloomery, blast furnace, steel, Create Brass tier, the world map, the Twilight Forest | Steel Heart |
| Arcane Age | Occultism, Ars Nouveau, Theurgy, Apotheosis, the Nether | Arcane Keystone |
| Industrial Age | Immersive Engineering, coke and FE power, black steel, Create packages | Pressure Core |
| Electric Age | IE HV, Arc Furnace, Excavator, oil and diesel | Humming Core |
| Information Age | Mekanism, Applied Energistics 2, Mystical Agriculture, the End | Data Matrix |
| Space Age | Fission, Ad Astra rockets, the Moon and Mars, Draconic Evolution | Star Chart |
| Quantum Age | Fusion, antimatter, Evolved Mekanism, the outer planets | Quantum Core |
| Singularity Age | Chaotic Draconic gear, the Draconic Reactor, a Stargate | the final boss |

Items, blocks and recipes of a locked Age are hidden in EMI and cannot be used. Machines follow the same rule: no machine produces anything from an Age you have not reached, and automated miners only dig the ores of your Ages. Ores of a later Age sit in the TerraFirmaCraft veins of your world from day one, disguised as rock until their Age. Everything you need in quantity has an automated way, so the pack stays hard without turning into a grind. The quest book is on **K**: every Age is a chapter with three or four main strands that friends can split up, side lines, and one goal quest.

The world comes first. In Dawn you see no recipe viewer, no block overlay, no mod messages and no advancement popups; EMI and Jade open with the Stone Age. A small line in the corner names your Age and the offering it asks for, the quest book wears a night sky with the Shrine of your Age and lists only the Ages you have reached. TerraFirmaCraft keeps its food decay and tool wear, but there is no thirst, and a one-sided diet never costs you health (a varied one still adds some).

## The Shrine

Each signature item is not crafted into the next tier. It is **offered at the Shrine**, an altar you build at the heart of your base and extend with one ring per Age, from a hearth circle of cobblestone and thatch to a ring of quantum casings. When the ring is complete, the offering lies on its plinth and the Age's rite is fulfilled, your team prays together at the Shrine Heart. Caelum, the god of the firmament, answers: a beam of light, a changed sky, a new Age.

The accepted ring is consecrated: its blocks turn, from the heart outward, into one sky-marble structure with the Age's colour accent. Consecrated blocks cannot be broken, blown up or moved; to rebuild, sneak and punch the Shrine Heart with an empty hand for 60 seconds of maintenance mode, and a broken block gives back its original material. Every awakened ring adds a blessing around the shrine, starting with a circle where no monsters spawn, later faster walking, more health, more XP, faster mining, longer reach, softer falls and luck. The offerings stay on the shrine as relics. You will need all nine again at the end.

## The Finale

In the Singularity Age the relics come off their plinths and are fused into the **Ultimate Singularity**, powered by a Draconic Reactor that the pack's own Reactor Controller keeps fuelled. The Singularity is the heart of a Stargate. Dial the address of **The Origin**, a dimension of floating islands made of the stone of every Age, and face the waves of the gateway, Maledictus and Ignis, and finally The Primordial.

## Requirements

- **Minecraft 1.21.1, NeoForge 21.1.252, Java 21.** The CurseForge App sets up NeoForge and Java for you.
- **RAM:** 10 GB for the game is recommended; set it in the CurseForge App under *Settings → Minecraft → Java Settings* (the default of 4 GB is not enough). A PC with 16 GB of RAM is the practical minimum.
- **207 mods.** The first start takes a few minutes.
- **Dedicated server:** 10 GB heap recommended. A ready-to-run server pack is attached to every release as an additional file.

## Multiplayer

Firmament Ages is written for **one team per server** (FTB Teams). The Ages are server-wide: the shrine, the ceremony and every unlock belong to the whole team, and a player who joins later is in the same Age as everybody else. Two competing teams on one server are not supported. Single player works like a team of one.

## Beta: what to expect

This is a beta. The whole progression is playable from Dawn to the final boss, and every Age, every ring of the shrine, the reactor controller and The Origin have been walked through on a dedicated server. Balance and texts will still change. Known limitations:

- Unlocking an Age reloads the server's datapacks. Expect a freeze of about six to eight seconds during the ceremony, and a slightly longer one for the first unlock after a server start.
- Draconic Evolution and Stargate Journey are beta mods on 1.21.1. Back up your world before the Space Age.
- The consecrated shrine, the animated textures and the ceremony visuals have been verified with renders and on the server, not yet in a long client session. Report what looks wrong.
- The final fight in The Origin has been run with its waves spawned by command; a full real-player run is still ahead. Gateways to Eternity runs the CurseForge build of 5.1.0, which is older than the Modrinth build of the same version.
- The relics do not float into the injectors on their own: from the Singularity Age on, sneak and use a plinth with an empty hand to take its relic back, then feed them to the fusion crafter. The shrine has no animated idol and the ceremony uses vanilla sounds.
- Osmium, the ore of the Information Age, comes from the pack's own TerraFirmaCraft vein since 0.6.2 and has seen little play testing.
- The loading and title screens, the quest book's look, the Age indicator and the welcome screen are new in 0.7.0 and have been checked on the server and in code, not yet in a long client session.
- The server pack is about 510 MB.

Please report bugs on the [issue tracker](https://github.com/PhoenixFlyy/firmament-ages/issues).

## Built with

Firmament Ages Core (firmages-core), the pack's own mod, runs the Ages: the shared Age state, the machine-recipe gate, the miner filter, the Shrine and its ceremony, the Draconic reactor refuelling, The Origin and what players see of it all (Age indicator, welcome, EMI and Jade by Age). TerraFirmaCraft carries the early game, Create the kinetics and logistics, Immersive Engineering, Mekanism and Draconic Evolution the tech ladder, Occultism and Ars Nouveau the magic, ProgressiveStages and KubeJS the gating, FTB Quests the quest book.

## Links

- Source (quests, scripts, configs and the mod): [github.com/PhoenixFlyy/firmament-ages](https://github.com/PhoenixFlyy/firmament-ages)
- Issues: [github.com/PhoenixFlyy/firmament-ages/issues](https://github.com/PhoenixFlyy/firmament-ages/issues)
- Firmament Ages Core on CurseForge: the pack's own mod, listed as a dependency of every release

## License

The pack's own parts, the quests, KubeJS scripts, configs, textures and Firmament Ages Core, are released under the MIT License (the `LICENSE` file of the source repository). Every other mod belongs to its authors and keeps its own license; the credits below name each one.

## Shaders

Firmament Ages starts with shaders turned on: it uses Complementary Shaders – Reimagined (r5.9.3) by EminGT and Complementary Development, unmodified and installed through CurseForge's own system ([complementary.dev](https://www.complementary.dev/), [CurseForge](https://www.curseforge.com/minecraft/shaders/complementary-reimagined)). Any problem caused by using it in this modpack is the modpack's responsibility, so please report it to us and not to Complementary. For weaker PCs the pack also includes MakeUp – Ultra Fast by XavierFST (LGPL-3.0); press O in game to switch shader packs or to turn shaders off. Shaders switch off by themselves on Ad Astra planets and in the Origin.

## Credits

<!-- credits:start -->

Firmament Ages 0.8.1 contains 207 mods. Thank you to every author.

- **[NoCube's] Explore: TFC Ruins - Structures for TerraFirmaCraft** by NoCube ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tfc-ruins))
- **Ad Astra** by AlexNijjar ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ad-astra))
- **Advanced TFC Tech Unofficial** by HermitOwO ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/advanced-tfc-tech-unofficial))
- **AdvancedAE** by pedroksl ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/advancedae))
- **AE2 Draconic Fusion Autocrafter** by Franchino961 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ae2-draconic-fusion-autocrafter))
- **AllTheLeaks (Memory Leak Fix)** by Uncandango ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/alltheleaks))
- **Almost Unified** by AlmostReliable, Relentless, Lytho ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/almostunified))
- **Alternating Flux NeoForge** by Arctonix ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/alternating-flux-neoforge))
- **Apotheosis** by Shadows_of_Fire ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/apotheosis))
- **Apothic Attributes** by Shadows_of_Fire ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/apothic-attributes))
- **Apothic Enchanting** by Shadows_of_Fire ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/apothic-enchanting))
- **Apothic Spawners** by Shadows_of_Fire ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/apothic-spawners))
- **Applied Energistics 2** by thetechnici4n, shartte, TeamAppliedEnergistics ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2))
- **Applied Energistics 2 Wireless Terminals** by Mari_023 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2-wireless-terminals))
- **Applied Mekanistics** by ramidzkh, shartte, thetechnici4n ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-mekanistics))
- **ArborFirmaCraft** by Therighton ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/arborfirmacraft))
- **Architectury API** by shedaniel, Juicebus, MaxNeedsSnacks, st0x0ef ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/architectury-api))
- **Ars Additions** by Jarva ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ars-additions))
- **Ars Creo** by baileyholl2 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ars-creo))
- **Ars Nouveau** by baileyholl2, Gootastic ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau))
- **Ars Skyweave Shader Fix** by Kasch_X ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ars-sky-shader-fix))
- **Astikor Carts TFC** by Verph ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/astikor-carts-tfc))
- **AstikorCarts Redux** by jusipat ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/astikor-carts-redux))
- **AU: Immersive Engineering** by Relentless ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/almost-unified-ie))
- **Azimuth API** by CakeIsTastyDeveloper ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/azimuth-api))
- **BadOptimizations** by thosea ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/badoptimizations))
- **Balm** by BlayTheNinth ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/balm))
- **Barrels 2012** by EERussianguy ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/barrels-2012))
- **Beneath** by EERussianguy ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/beneath))
- **Better Advanced Tooltips** by Lat ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/better-advanced-tooltips))
- **Better Compatibility Checker** by Gaz_ ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/better-compatibility-checker))
- **Block Runner** by Fuzs ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/block-runner))
- **Bosses of Mass Destruction [Forge | NeoForge]** by Cerbon ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/bosses-of-mass-destruction-forge))
- **Brandon's Core** by brandon3055 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/brandons-core))
- **Cave Dweller ReEvolved** by UnanimatedGif ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/cave-dweller-reevolved))
- **CERBON's API [Forge | Fabric | NeoForge]** by Cerbon ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/cerbons-api))
- **Certain Questing Additions** by HollowHorizon ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/certain-questing-additions))
- **Chunky (Forge/NeoForge)** by pop4959 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/chunky-pregenerator-forge))
- **Cloth Config API (Fabric/Forge/NeoForge)** by shedaniel, LinkieIsBetterThanK9 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/cloth-config))
- **Clumps** by Jaredlll08 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/clumps))
- **CodeChicken Lib 1.8.+** by covers1624, Chicken_Bones ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/codechicken-lib-1-8))
- **Colorwheel** by djefrey ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/colorwheel))
- **Common Storage Lib** by terrariumearth, CodexAdrian ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/common-storage-lib))
- **Controlling** by Jaredlll08 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/controlling))
- **CorgiLib** by Corgi_Taco, JT122406 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/corgilib))
- **Create** by simibubi, IThundxr ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create))
- **Create Aeronautics** by ryanhcode, singularity_cyvack, Starlotte_117, keyberries, beeisyou, Eriksonn, kyanbirb ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-aeronautics))
- **Create Big Cannons** by rbasamoyai ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-big-cannons))
- **Create Contraption Terminals** by tom54541 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-contraption-terminals))
- **Create Crafts & Additions** by MRHminer ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/createaddition))
- **Create Deco** by talrey, Baileybun ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-deco))
- **Create Encased** by iglee42 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-encased))
- **Create Factory Logistics** by zznty ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-factory-logistics))
- **Create Jetpack** by possible_triangle ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-jetpack))
- **Create Mechanical Extruder** by oierbravo ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-mechanical-extruder))
- **Create Mechanical Spawner** by oierbravo ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-mechanical-spawner))
- **Create Railways Navigator** by MrJulsen ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-railways-navigator))
- **Create Stock Bridge** by tom54541 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-stock-bridge))
- **Create Train Utilities (Create Train Doors)** by Adeptstack ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-trainutilities))
- **Create: Applied Kinetics** by Forsteri123 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-applied-kinetics))
- **Create: Bells & Whistles** by a0ax, rdh, aesefficio, Fatasiangamer ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/bellsandwhistles))
- **Create: Bits 'n' Bobs** by az_tech, CakeIsTastyDeveloper, NormalestGuy, AstralButOnCurseForge, shartbarn ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-bits-n-bobs))
- **Create: Blocks & Bogies** by WeidosOddities ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-blocks-bogies))
- **Create: Connected** by Lysine ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-connected))
- **Create: Copycats+** by Lysine, Bennyboy1695, Redcat ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/copycats))
- **Create: Design n' Decor** by Luna, Dr_Mango_Tea ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-design-n-decor))
- **Create: Diesel Generators** by George_VI ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-diesel-generators))
- **Create: Dragons Plus** by DragonsPlus, RaymondBlaze ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-dragons-plus))
- **Create: Enchantable Machinery** by Cotrin ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-enchantable-machinery))
- **Create: Enchantment Industry** by DragonsPlus, MarbleGate, RaymondBlaze ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-enchantment-industry))
- **Create: Framed** by DakotaPrideModding ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-framed))
- **Create: Hypertubes** by Rokzin ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/hypertubes))
- **Create: Liquid Fuel** by Forsteri123 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-liquid-fuel))
- **Create: Mobile Packages** by timplay33 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-mobile-packages))
- **Create: Occult Engineering** by irishgreencitrus ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-occult-engineering))
- **Create: Pattern Schematics** by CakeIsTastyDeveloper ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-pattern-schematics))
- **Cucumber Library** by BlakeBr0 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/cucumber))
- **Curios API** by TheIllusiveC4 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/curios))
- **Data Anchor** by Corgi_Taco ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/data-anchor))
- **Default Options** by BlayTheNinth ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/default-options))
- **Draconic Evolution** by brandon3055 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/draconic-evolution))
- **Draconic Evolution Render Patcher** by yrley ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/draconic-evolution-render-patcher))
- **DragonLib** by MrJulsen ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/dragonlib))
- **Drippy Early Loading Module** by Keksuccino ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/drippy-early-loading-module))
- **Drippy Loading Screen** by Keksuccino ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/drippy-loading-screen))
- **Dynamic FPS** by juliand665, LostLuma ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/dynamic-fps))
- **EMI** by EmilyPloszaj ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/emi))
- **EMI Loot** by fzzyhmstrs ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/emi-loot))
- **Engineered Schematics** by Muddykat ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/engineered-schematics))
- **Enhanced Celestials 2: Core** by Corgi_Taco ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/enhanced-celestials-2-core))
- **Enhanced Celestials 2: Default Lunar Events** by Corgi_Taco ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/enhanced-celestials-2-default-lunar-events))
- **Enhanced Celestials 2: Shader Support** by Corgi_Taco ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/enhanced-celestials-2-shader-support))
- **Entity Culling Fabric/Forge** by tr7zw ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/entityculling))
- **Evolved Mekanism** by iglee42, WoXayZ ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/evolved-mekanism))
- **ExtendedAE** by GlodBlock ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ex-pattern-provider))
- **FancyMenu** by Keksuccino ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/fancymenu))
- **FastSuite** by Shadows_of_Fire ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/fastsuite))
- **FerriteCore ((Neo)Forge)** by malte0811 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ferritecore))
- **FirmaLife** by EERussianguy ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/firmalife))
- **Firmament Ages Core (firmages-core)** by PhoenixFlyy (part of this pack)
- **FramedBlocks** by XFactHD ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/framedblocks))
- **FTB Chunks (NeoForge)** by FTB, FTBTeam ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-forge))
- **FTB Library (NeoForge)** by FTB, FTBTeam ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-library-forge))
- **FTB Quests (NeoForge)** by FTB, FTBTeam ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge))
- **FTB Teams (NeoForge)** by FTB, FTBTeam ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge))
- **FTB XMod Compat** by FTB, FTBTeam ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-xmod-compat))
- **Fzzy Config** by fzzyhmstrs ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/fzzy-config))
- **Gateways to Eternity** by Shadows_of_Fire ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/gateways-to-eternity))
- **GeckoLib** by Gecko, EliotL ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/geckolib))
- **Glodium** by GlodBlock ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/glodium))
- **GraveStone Mod** by henkelmax ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/gravestone-mod))
- **GuideME** by shartte ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/guideme))
- **Hostile Neural Networks** by Shadows_of_Fire ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/hostile-neural-networks))
- **Iglee's Library** by iglee42 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/iglee-library))
- **ImmediatelyFast** by RaphiMC ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/immediatelyfast))
- **Immersive Engineering** by BluSunrize, Sky_Som, malte0811 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-engineering))
- **Immersive Petroleum** by Flaxbeard ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-petroleum))
- **Immersive Posts** by TwistedGate ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersiveposts))
- **Iris Block Compat** by juds ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/iris-block-compat))
- **Iris Shaders** by coderbot, IMS21 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/irisshaders))
- **Iris Veil Compat** by leon_mout ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/iris-veil-compat))
- **Jade 🔍** by Snownee ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/jade))
- **Just Enough Immersive Multiblocks** by aronson_1 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/just-enough-immersive-multiblocks))
- **Just Enough Items (JEI)** by mezz ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/jei))
- **Just Enough Mekanism Multiblocks** by gisellevonbingen ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/just-enough-mekanism-multiblocks))
- **Konkrete** by Keksuccino ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/konkrete))
- **Kotlin for Forge** by thedarkcolour ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge))
- **KubeJS** by Lat ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/kubejs))
- **KubeJS Additions** by ILIKEPIEFOO2 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/kubejs-additions))
- **KubeJS Create** by Lat ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/kubejs-create))
- **KubeJS Mekanism** by Lat ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/kubejs-mekanism))
- **KubeJS TFC** by NotEnoughMail ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/kubejs-tfc))
- **L_Ender 's Cataclysm** by mcl_ender ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/lendercataclysm))
- **LambDynamicLights** by LambdAurora ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/lambdynamiclights))
- **Lionfish API** by mcl_ender ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/lionfish-api))
- **Lithium (Fabric/NeoForge)** by JellySquid ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/lithium))
- **LootJS: KubeJS Addon** by Lytho ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/lootjs))
- **Mechanicals Lib** by oierbravo ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mechanicals-lib))
- **MEGA Cells** by ninety ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mega-cells))
- **Mekanism** by bradyaidanc, thommy101, pupnewfster, Thiakil ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mekanism))
- **Mekanism Additions** by bradyaidanc, pupnewfster, Thiakil, thommy101 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mekanism-additions))
- **Mekanism Generators** by bradyaidanc, thommy101, pupnewfster, Thiakil ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mekanism-generators))
- **Mekanism Lasers** by MrOminox_ ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mekanism-lasers))
- **Mekanism Tools** by bradyaidanc, pupnewfster, Thiakil, thommy101 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mekanism-tools))
- **Mekanism: Neutron Activator** by michanide ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mekanism-neutron-activator))
- **Mekanism: Ponders** by alyx20 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mekanism-ponders))
- **Mekanism:More Machine** by lost_myself8 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mekanism-more-machine))
- **Melody** by Keksuccino ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/melody))
- **MezzConfig** by mezz ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mezzconfig))
- **ModernFix** by embeddedt ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/modernfix))
- **Modonomicon** by kli_kli ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/modonomicon))
- **More Darkness** by payangar_dev ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/more-darkness))
- **More Immersive Wires** by tom54541 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/more-immersive-wires))
- **More Mekanism Processing** by gisellevonbingen ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/more-mekanism-processing))
- **Mouse Tweaks** by YaLTeR ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mouse-tweaks))
- **Mowzie's Mobs** by bobmowzie, noonyeyzz, wadoo154, pau101, vakypanda ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mowzies-mobs))
- **Mystical Agradditions** by BlakeBr0 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mystical-agradditions))
- **Mystical Agriculture** by BlakeBr0 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mystical-agriculture))
- **Mystical Customization** by BlakeBr0 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mystical-customization))
- **Occultism** by kli_kli ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/occultism))
- **Occultism KubeJS** by kli_kli, MaxNeedsSnacks ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/occultism-kubejs))
- **Packet Fixer** by TonimatasDEV ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/packet-fixer))
- **Patchouli** by Vazkii ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/patchouli))
- **Placebo** by Shadows_of_Fire ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/placebo))
- **Polymorph** by TheIllusiveC4 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/polymorph))
- **Precision Prospecting** by NotEnoughMail ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/precision-prospecting))
- **ProgressiveStages** by EnVyOnMyMind ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/progressivestages))
- **Puzzles Lib** by Fuzs ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/puzzles-lib))
- **Resourceful Config** by ThatGravyBoat, epic_oreo ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/resourceful-config))
- **Resourceful Lib** by ThatGravyBoat, epic_oreo ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/resourceful-lib))
- **Rhino** by Lat ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/rhino))
- **Ritchie's Projectile Library** by rbasamoyai ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ritchies-projectile-library))
- **Roads and Roofs TFC** by Therighton ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/roads-and-roofs-tfc))
- **Sable** by ryanhcode, Eriksonn ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/sable))
- **Searchables** by Jaredlll08 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/searchables))
- **Simple Backups** by MelanX ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-backups))
- **SmartBrainLib (Forge/Fabric/Quilt)** by Scimiguy ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/smartbrainlib))
- **Sodium** by JellySquid ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/sodium))
- **Sophisticated Backpacks** by P3pp3rF1y ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks))
- **Sophisticated Backpacks Create Integration** by P3pp3rF1y ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks-create-integration))
- **Sophisticated Core** by P3pp3rF1y ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/sophisticated-core))
- **spark** by Iucko ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/spark))
- **Stargate Journey** by Povstalec ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/sgjourney))
- **Steam 'n' Rails Neoforge** by PoppyBlossom, Chameleon538 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/steam-n-rails-neoforge))
- **Strut Your Stuff (Struts)** by CakeIsTastyDeveloper ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/strut-your-stuff))
- **Summoning Rituals** by Relentless ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/summoningrituals))
- **SuperMartijn642's Config Lib** by SuperMartijn642 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/supermartijn642s-config-lib))
- **SuperMartijn642's Core Lib** by SuperMartijn642 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/supermartijn642s-core-lib))
- **TerraFirmaCraft** by Dries007, EERussianguy, Therighton ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/terrafirmacraft))
- **TerraFirmaCraft AnvilHelper** by Fox2Code ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/terrafirmacraft-anvilhelper))
- **TFC + IE Crossover** by Nmagpie ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tfc-ie-crossover))
- **TFC Better Blast Furnace** by tadashi_3 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tfc-better-blast-furnace))
- **TFC Registry API** by Verph ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tfc-registry-api))
- **TFC Regrowing Forests** by Verph ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tfc-regrowing-forests))
- **TFC Support Indicator** by floweynt ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tfc-support-indicator))
- **TFC Vessel Tooltip** by HermitOwO ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tfc-vessel-tooltip))
- **TFC Weld Button** by HermitOwO ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tfc-weld-button))
- **TFCreate Compat** by Ayaibu ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tfcreate-compat))
- **The Twilight Forest** by Benimatic, Drullkus, Squiggly_Androsa, Tamaized, GizmoTheMoonPig, jodlodi ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/the-twilight-forest))
- **Theurgy** by kli_kli ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/theurgy))
- **Theurgy KubeJS** by kli_kli ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/theurgy-kubejs))
- **Tom's Simple Storage Mod** by tom54541 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/toms-storage))
- **Torque Link Create To TFC** by Vvxzv ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/torque-link-create-to-tfc))
- **Totemic** by ljfa2, Forge_User_22859052 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/totemic))
- **Trash Cans** by SuperMartijn642 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/trash-cans))
- **WoodenCog** by Manwe_b ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/woodencog))
- **YetAnotherConfigLib** by isXander ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/yacl))

<!-- credits:end -->
