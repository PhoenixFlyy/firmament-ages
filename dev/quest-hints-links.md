# Quest-Hinweise der Querverbindungen (Harmonie-Paket A9)

Für die Questrunde (B2): je Verbindung des Harmonie-Plans (Abschnitt 4) ein englischer Hinweis, so wie er in eine
Quest des Strangs geht. Stil nach `dev/style-guide.md` (Imperativ, kein Modname, höchstens 25 Wörter je Schritt).
Die Rezepte stehen in `kubejs/server_scripts/recipes/age_N/links.js`; `dev/poc_analyze.py` K-1 und R-K prüfen sie.
Verbindung 23 (Erzprozessor) und 24 (Arkan-Katalysator) gehören dem Endspiel-Paket A10 und fehlen hier; Verbindung 22
(Wählkosten des Sternentors) setzt A10 als Zahl, der Hinweis steht trotzdem unten.

| # | Age | Quest (Vorschlag) | Hinweis (Englisch, für den Questtext) |
|---|---|---|---|
| 1 | age_1 | Bronze Age, Strang Create: "Torque" | Set a stress converter on a shaft and let the wheel turn your quern and bellows. Hands are for better work now. |
| 2 | age_2 | Iron Age, Strang Create: "The Smithing Die" | Press a hot ingot under the mechanical press: two rods, as from the anvil. Trains and precision mechanisms eat rods by the stack. |
| 3 | age_3 | Arcane Age, Spirit Route: "Bound by Gears" | The Foliot and Djinni binding books each take a precision mechanism. The machine line builds the miner of the spirits. |
| 4 | age_3 | Arcane Age, Spirit Route: Mineshaft | The Mineshaft brings ore pieces, never ingots. Crush them or work them in the spagyric line for more metal. |
| 5 | age_3 | Arcane Age, Nebenquest Source | Move Source through a Source jar network or, from the Information Age, through the ME network. Machines carry magic, magic never carries machines. |
| 6 | age_4 | Industrial Age, Strang Power: "Rubber" | Tap latex from hevea or rubber fig and boil it with sulfur. Insulated wire, conveyors and the best belts take the rubber bar. |
| 7 | age_5 | Electric Age, Strang Oil: "Diesel Generator" | Each generator block holds an alternator from the Industrial Age. Build the old engine into the new one. |
| 8 | age_5 | Electric Age, Strang Ore: "Osmium Vein" | Sample for an osmium vein and set an excavator on it. Its osmium feeds the casings of the next Age. |
| 9 | age_6 | Information Age, Factory: "Steel Casing" | Build a steel casing around a heavy engineering block. Every machine of this Age stands on the work of the last. |
| 10 | age_6 | Information Age, Factory: "Control Circuit" | Lay osmium and redstone on a circuit board in the grid or the assembler. Two basic control circuits come out. |
| 11 | age_6 | Information Age, Factory: "Ethene from Oil" | Crack naphtha in the hydrotreater into ethene, then condense it into gas. It burns hotter than diesel and makes HDPE. |
| 12 | age_6 | Information Age, Factory: "Powder to Dust" | Feed 64 mineral powder from the crusher into the enrichment chamber. It returns one metal dust. |
| 13 | age_6 | Information Age, Nebenquest Mystical Agriculture | Metal essence gives ore pieces, not ingots. Run them through the ore lines like any mined piece. |
| 14 | age_6 | Information Age, Digital: "ME Controller" | The controller rests on a steel casing, the drive on an advanced control circuit. The network grows from the factory. |
| 15 | age_7 | Space Age, Atomic: "Digital Miner" | Build the Digital Miner on the mechanical crafter. Gears built your first factory; they still build the last machines. |
| 16 | age_7 | Space Age, Rocket: "Fuel by Tier" | Fill tier 1 and 2 rockets with diesel or kerosene. Tier 3 and 4 burn liquid hydrogen or cryo fuel. |
| 17 | age_7 | Space Age, Factory: "Elite Installer" | The elite tier installer takes a desh plate, the ultimate one calorite. The planets lift your machines. |
| 18 | age_7 | Space Age, Atomic: "Fission Port" | Each fission port holds an advanced electrolysis core, the heart of the large gas generator. Turbine casings take steel sheet metal. |
| 19 | age_7 | Space Age, Fusion Crafting: "Wyvern Injector" | The wyvern injector takes an engineering processor from your network. Patterns of the last Age guide the fusion. |
| 20 | age_8 | Quantum Age, Fusion: "Fusion Controller" | The fusion controller holds a fission port; the awakened core takes a 256k storage component. Old power, old patterns. |
| 21 | age_8 | Quantum Age, Antimatter: "Supercritical" | The phase shifter needs a million joules for each drop of polonium. Only a fusion plant feeds it. |
| 22 | age_9 | Singularity Age, Stargate: "Dial Home" | Every dial drains the gate's store. Feed it from your reactor before you dial. |
| 25 | age_4 bis age_9 | je Age ein Schritt "Age Component" | Industrial: Arcane Gearbox. Electric: Attuned Circuit, also in the huge diesel engine. Information: advanced control circuit, four per Attuned Circuit. Space: wyvern core. Quantum: awakened core. Singularity: chaotic core. |

## Die Age-Komponenten (Verbindung 25)

| Age | Komponente | Aus der Vor-Age | Opfergabe | Fremde Maschine |
|---|---|---|---|---|
| age_4 | Arcane Gearbox (`firmages:arcane_gearbox`) | Getriebe, Präzisionsmechanismen, Stahl | Pressure Core | IE Reinforced Blast Brick (Improved Blast Furnace) |
| age_5 | Attuned Circuit (`firmages:attuned_circuit`) | IE-Platine, fortgeschrittene Elektronik, Elektrumdraht | Humming Core | Huge Diesel Engine (neu) |
| age_6 | Advanced Control Circuit (`mekanism:advanced_control_circuit`) | Basic Control Circuit (aus IE-Platine), Attuned Circuit | Data Matrix (drei) | ME Drive (neu), jede fortgeschrittene Mekanism-Maschine |
| age_7 | Wyvern Core (`draconicevolution:wyvern_core`) | Advanced Control Circuit, Draconium | Star Chart | Wyvern-Injektor, Marid-Bindebuch |
| age_8 | Awakened Core (`draconicevolution:awakened_core`) | Wyvern Cores, AE2-256k-Komponente (neu) | Quantum Core (Katalysator) | Reaktor-Stabilisator des Draconic-Reaktors, Chaotic Core |
| age_9 | Chaotic Core (`draconicevolution:chaotic_core`) | vier Awakened Cores, große Chaos-Fragmente | Ultimate Singularity (Katalysator) | Sternentor-Basisblock (über die Ultimate Singularity) |
