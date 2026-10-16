# Harmonie Runde 2: Erzleiter, ein Eisen, Brennstoff-Tabelle, Mekanism x10, Miner gegen Leiter

Stand 2026-10-16, Branch `c2/harmonie` (auf dev ff67812, Pack 0.11.0). Pakete A4, A5, A6 und H2A aus dem
Harmonie-Plan (`harmonie-plan.md` im Scratchpad der Sitzung) plus Felix' Auftrag „Miner gegen Leiter“. Entscheidungen
mit Rückweg: `dev/decisions-while-away.md` (2026-10-16), Belege: `dev/poc-results.md` Abschnitt „1.0 round C2,
harmony“. Kürzel: [J] aus Jar/Config gelesen, [B] berechnet, [M] auf dem Server gemessen.

## 1. Erzleiter aus der Metalltabelle (A4)

`kubejs/data/firmages/balance/metals.json` (vorher `dev/data/metals.json`) ist die eine Quelle. Die Rezeptskripte
lesen sie beim Spielstart über `kubejs/startup_scripts/metal_table.js` (`global.FA_METALS`: `factor`, `perPiece`,
`ratio`); `dev/poc_analyze.py` liest dieselbe Datei für L-1 bis L-7. Die Konstanten `YIELD = 1.7`, `GRADE_MB`,
`DIGEST` und `ratio()` in den Skripten sind weg.

| Age | Station | Faktor | Woher im Code |
|---|---|---|---|
| age_0 | TFC-Tiegel | 1,0 | TFC |
| age_2 | Create Crushing Wheels | 1,5 (war 1,7) | `age_2/iron_age.js` |
| age_3 | Theurgy Digestion Vat (Magie, H3A) | 2,0 (C1, geprüft) | `age_3/arcane_age.js` |
| age_4 | IE Crusher + Quernpulver + 5 % Nebenmetall | 2,5 (war 2,0) | `age_4/industrial_age.js` |
| age_6 | Mekanism Purification | 3,0 | `age_6/ore_ladder.js` |
| age_7 | Mekanism Injection | 4,0 | `age_6/ore_ladder.js` (`requireAge age_7`) |
| age_8 | Mekanism Dissolution | 5,0 | `age_6/ore_ladder.js` (`requireAge age_8`) |

- **Pulver:** Der IE Crusher gibt das TFC-Mineralpulver in Quernmenge (2/3/5/7 je Grad) als Hauptausgabe. Ab der
  Informationszeit macht die Enrichment Chamber 64 Pulver zu einem kanonischen Staub (15 Rezepte
  `firmages:enriching/*_powder`, Plan-Link 12). IE-Linie damit 2,5 + 2,5 × 20/64 = 2,81 < 3,0. TFC + IEs
  Lichtbogen-Rezepte „20 Pulver → Barren“ sind entfernt; mit ihnen läge die IE-Linie in der Elektrikzeit bei 3,5.
- **Lichtbogenofen:** jeder Staub → Barren für 6.400 FE statt 51.200 (Metalltabelle `arc_furnace.dust_energy`,
  `age_5/electric_age.js`, 16 Rezepte im Dump, L-7).
- **Mineshaft:** Stücke aller Grade, Gewichte klein 15 / arm 35 / normal 35 / reich 15 je Hundert, Mittel 20,75 mB
  statt 35 (60 Rezepte `firmages:miner/ores/*`, L-6).
- **Mekanism-Leiter:** Purification/Injection/Dissolution nehmen die TFC-Stücke aller Grade (C-6a: 16 Familien × 4
  Grade). Die Barren am Ende sind die kanonischen Barren der Unifizierung (TFC für TFC-Metalle).

Gemessen [M] auf dem Dump mit allen Ages und Tor (38.582 Rezepte): L-1 13 Metalle, 68 Erzstücke, jeder Faktor ±5 %;
L-2 bester Technikfaktor age_0 bis age_9: 1,0 / 1,0 / 1,5 / 1,5 / 2,5 / 2,5 / 2,86–3,0 / 4,0 / 5,0 / 5,0, monoton; L-3 Magie ≤ nächste Technikstufe;
L-4 kein anderer Rezepttyp vervielfacht Erz; L-5 kein Metall ohne Erz.

## 2. Ein Eisen (A5)

`kubejs/server_scripts/recipes/unify_iron.js`:
- `replaceInput` `minecraft:iron_ingot` → `#c:ingots/iron` über alle Rezepte; drei Typen, deren Zutaten verpackt sind
  (`advanced_ae:skysteel`, `sgjourney:crystallizing/tollan_upgrade_crystal`, `mysticalagriculture:enchanter/impaling`),
  werden als JSON umgeschrieben. Ohne das hätte die Ableitung das Tollan-Stargate versteckt (erster Dump).
- Eisenstaub: Ofen, Hochofen und Create-Lüfter → `tfc:metal/ingot/cast_iron` (Occultisms zwei Rezepte); Schmiedeeisen
  nur im Lichtbogenofen (age_5) und im Energized Smelter (`firmages:smelting/wrought_iron_from_dust`, age_6; Mekanism
  sucht eigene `mekanism:smelting`-Rezepte vor den Ofenrezepten, laut Quelle 10.7.19, im Spiel nicht geprüft).
- `minecraft:iron_ingot` steht in `[hidden]` von `dev/age_map.toml`; das Tor wirft alle Rezepte, die ihn noch machen
  (Vanilla-Erz schmelzen, Apotheosis-Bergung, Nukleosynthese, Create-Präzisionsmechanismus-Fehlteil).
- Pack-Dateien `kubejs/data/ad_astra/recipe/iron_panel.json`, `iron_factory_block.json` nehmen das Tag.

Gemessen [M]: U-2 0 lebende Rezepte nehmen oder machen `minecraft:iron_ingot`; 394 lebende Rezepte nehmen
`#c:ingots/iron`. Vanilla-Eisen aus Beute (Truhen anderer Mods) bleibt ein totes Item (offener Punkt).

## 3. Brennstoff-Tabelle (A6)

`kubejs/data/firmages/balance/fuels.json` → `dev/gen_fuels.py` schreibt IE `generator_fuel` (Kreosot, Kerosin,
Diesel, schwefelhaltiger Diesel), die CDG-Dieselmotoren (`fuel_type/diesel.json`), die Mekanism-Datenmap
`chemical_attribute_fuel.json` (Ethylen, Wasserstoff) und den Turbolader-Verbrauch in
`config/createdieselgenerators-server.toml`. `--check` vergleicht jede Route mit der Tabelle (±25 %) und die Leiter.

| Brennstoff | FE je Bucket | Routen [B] |
|---|---|---|
| Kreosot | 81.920 | IE-Generator 20 Ticks |
| Kerosin | 851.968 | IE-Generator 208 Ticks |
| Diesel (schwefelhaltig) | 1.167.360 | IE-Generator 285 Ticks |
| Diesel | 1.318.912 | IE 322 Ticks; CDG normal 1.312.500 (−0,5 %), modular 1.312.500 (−0,5 %), groß 1.322.835 (+0,3 %) |
| Ethylen | 11.280.000 | Mek Gas-Burning Generator 705 J/t × 40 Ticks je mB |
| D-T (Fusion) | 200.000.000 | nur Leiter-Grenze |

Leiter Kreosot < Diesel < Ethylen < D-T hält. CDG über C&A-Alternator: FE je SU = 480 × 0,35 / 16.384 = 0,010254
[J]; Burn-Rate = Motor-SU × FE/SU / FE je mB: normal 0,048, modular 0,064, groß 0,127 mB/t (war 0,05 / 0,05 / 0,11;
der modulare Motor gab 1,68 M FE je Bucket). Turbolader: Verbrauch ×2 wie das Tempo ×2. Biodiesel, Benzin, Pflanzenöl
und Ethanol macht im Pack nichts (Liste `dead`); der Check mit `--dump-dir` meldet jeden neuen IE-Brennstoff.

## 4. Mekanism x10 (H2A) und Verbraucher-Bänder

`dev/gen_machine_usage.py` setzt in den mitgelieferten vollen Config-Dateien (NeoForge 21.1 liest Server-Configs aus
`config/`) jeden Wert auf Mod-Standard × Faktor; `--check` vergleicht, `--server-dir` liest die Werte eines Servers
zurück. Evolved Mekanism hat keine eigene Verbrauchs-Config: Alloyer, Chemixer, Melter und Solidifier lesen
`combiner`, `pressurizedReactionBase` und `chemicalOxidizer` von Mekanism (javap `EMBlockTypes`) und folgen dem x10.

| Band | Maschinen (FE/t ohne Upgrades, vorher → jetzt) |
|---|---|
| klein 200–400 | Enrichment, Crusher, Combiner/Alloyer, Metallurgic Infuser, Energized Smelter, Sawmill, Rotary, Seismic Vibrator (je 20 → 200); Osmium Compressor, Pumpe, Plenisher, Assemblicator, Painting Machine (40 → 400) |
| mittel 800 | Purification, Chemical Oxidizer/Melter, Infuser, Washer, Isotopic Centrifuge, Nutritional Liquifier, Pigment Extractor/Mixer (80 → 800) |
| groß 1.600 | Injection, Dissolution, Crystallizer, Modification Station (160 → 1.600) |
| Sonder | Laser 4.000 → 40.000; Teleporter 400 + 4/Block → 4.000 + 40/Block (40.000 zwischen Dimensionen); Digital Miner 400 → 4.800 (Abschnitt 5; bis zum Review-Fix 0.12.0 36.000) |
| unverändert | Chargepad (Übertragungsrate), Antiprotonic Nucleosynthesizer und die große Variante (40.000, schon Quantenband), Dimensional Stabilizer (Chunkladen), Evolved Mekanism APT (40.000), More-Machine-Replikatoren (versteckt) |
| More Machine | Ambient Gas Collector 40 → 400, große Rotary 20 → 200, großer Chemical Infuser und Pigment Mixer 40 → 400 (+ versteckte Recycler, Planting Station, CNC, Presser ×10) |

Die Puffer (`machine-storage.toml`) wachsen mit demselben Faktor, sonst liefe eine Maschine mit Tempo-Upgrades nicht
mehr an (Mekanism prüft Puffer ≥ Verbrauch je Tick). Gemessen [M]: 282 Werte im gebooteten Server gleich den
ausgelieferten.

## 5. Miner gegen Leiter (Felix: „4x+ muss sich neben den unbegrenzten Minern lohnen“)

Mit unbegrenzten Minern ist Strom die Grenze einer Basis. Eine Leiterstufe lohnt sich, wenn sie die FE je Barren
senkt (Abbau + Verarbeitung). Rechnung und Prüfung: `dev/miner_ladder.py --dump-dir <dump> --check`. Zeiten aus den
Jars [J]: Mekanism 200 Ticks je Operation (Dissolution 100, Washer 1 mB/Tick), IE-Excavator ein Grab je 45/speed
Ticks (360° / 8 Schaufeln). TFC-Adern im Mittel 24 mB je Erzblock [J] (Eisen, Zinn, Gold; arme Kupferadern 18,5,
reiche 29,5). Chemikalien (O₂, HCl, H₂SO₄) nicht mitgezählt; sie verteuern nur die höheren Stufen.

**Review-Fix 0.12.0.** Mit 90.000 J/t (36.000 FE/t) kostete der Digital Miner (Weltraumzeit) 300.000 FE je Erz-mB,
etwa neunmal so viel wie der Excavator (Elektrozeit), und voll aufgerüstet 360.000 FE/t, das Dreifache einer ganzen
Spaltanlage. Die Leiterregel hielt nur, weil der spätere Miner nutzlos war. Neu: der Miner kostet das 1- bis 2-Fache
des Excavators je Erz-mB, die Leiter hält über die Verarbeitung (Energie-Upgrades, die das Questbuch ab der
Informationszeit lehrt).

**Weltraumzeit-Anlage (Paket A8) [J]:** Spaltreaktor 1.000.000 J Wärme je mB Brennstoff (`generators.toml`
`energyPerFissionFuel`), Wasserkühlung 0,2 / 10 J → 20.000 mB Dampf je mB Brennstoff (`HeatUtils`: Effizienz 0,2,
Enthalpie = `maxEnergyPerSteam` 10), Turbine höchstens 10 J je mB Dampf (`general.toml` `maxEnergyPerSteam`) →
200.000 J = **80.000 FE/t je mB/t Abbrand**. Eine Anlage mit 1 mB/t braucht 20.000 mB/t Dampf, also 16 Dispergierer
(je 1.280 mB/t, `disperserChemicalFlow`): eine Turbine mit 5×5 Innenfläche. Das ist das Band 50.000 bis 120.000 FE/t
des Harmonie-Plans. Fusion (Quantenzeit) braucht diese Entscheidung nicht. Live mit `/firmages energy audit` an
Referenzbauten gemessen ist das nicht (offener Punkt „Generator-FE/t per Audit“); die Formeln sind Mekanisms eigene,
die Werte die der Kopie-Boot-Config.

**Jetzt:**

| Miner | Einstellung | Durchsatz | FE je Erz-mB |
|---|---|---|---|
| Digital Miner | 12.000 J/t (4.800 FE/t), 200 Ticks je Block | 360 Blöcke/h ohne, 3.600 mit 8 Tempo-Upgrades (dann 48.000 FE/t mit 8 Energie-Upgrades = 0,6 mB/t Spaltbrennstoff); 8.640 bzw. 86.400 mB/h | 960.000 FE je Block bei gleich vielen Tempo- und Energie-Upgrades = 40.000 (1,48 × Excavator-Mittel) |
| IE Excavator | 4.096 FE/t, Tempo 0,5 (ein Grab je 90 Ticks), Ertrag 38.400 je Ader | 800 Grabungen/h; Mittel 13,6 mB je Grab | Mittel 27.021 (16.384 reichste Ader) |

FE je Barren (Abbau + Verarbeitung) [B, `miner_ladder.py` auf dem Gate-aus-Dump der Review-Runde]:

| Route | 2,5x | 3x | 4x | 5x |
|---|---|---|---|---|
| Digital Miner, Mekanism mit 8 Energie-Upgrades | 1,61 M | 1,35 M (−16,1 %) | 1,06 M (−21,6 %) | 0,92 M (−13,2 %) |
| Digital Miner, Mekanism ohne Upgrades | 1,61 M | 1,47 M (−8,7 %) | 1,56 M (+6,4 %) | 1,97 M (+26,2 %) |
| Excavator Mittel, Mekanism mit 8 Energie-Upgrades | 1,09 M | 0,91 M (−15,9 %) | 0,73 M (−20,0 %) | 0,66 M (−10,2 %) |
| Excavator reichste Ader, 8 Energie-Upgrades | 0,66 M | 0,56 M (−15,4 %) | 0,47 M (−16,8 %) | 0,44 M (−4,5 %) |
| Excavator Mittel, Mekanism ohne Upgrades | 1,09 M | 1,03 M (−4,8 %) | 1,24 M (+19,5 %) | 1,71 M (+38,3 %) |

Regeln im Check: jede Stufe senkt die FE je Barren um mindestens 5 % für den Digital Miner und die mittlere
Excavator-Ader, jeweils mit Energie-Upgrades; der Digital Miner kostet das 1- bis 2-Fache des Excavators je Erz-mB
(jetzt 1,48); voll aufgerüstet zieht er höchstens eine Weltraumzeit-Anlage (`--plant`, 80.000 FE/t). Ohne
Energie-Upgrades lohnt sich hinter beiden Minern nur 3x (Zeilen 2 und 5): diese Falle nennt das Questbuch jetzt
(„Energy Upgrades“ in der Informationszeit, „Ore Times Four“ und „Ore Times Five“).

## 6. Prüfungen dieser Runde

| Prüfung | Ergebnis |
|---|---|
| `poc_analyze.py --dump-dir <Tor an> --baseline <Tor aus>` | 107 PASS, 0 FAIL (u. a. L-1 bis L-7, U-2, R-X age_2 bis age_9, G-1 bis G-4) |
| `gen_fuels.py --check --dump-dir` | aktuell, alle Routen in ±1 %, 6 IE-Brennstoffe im Dump bekannt |
| `gen_machine_usage.py --check --server-dir` | aktuell, 87 Werte vom Standard abweichend, 282 Werte zurückgelesen |
| `miner_ladder.py --check` | jede Stufe senkt die FE je Barren |
| `derive_ages.py --check`, `gen_stage_locks.py --check` | aktuell |
| `selftest all` | 71 bestanden, 3 rot (Krone, bis M15 so gewollt) |
| `check_log.py` | FATAL 0, ERROR 8, alle dokumentiert, Rest 0 |

## 7. Zählnachweis: zwei Stunden einer Referenzlinie (Review-Fix 0.12.0)

Die Zahlen des Harmonie-Plans (512 / 2.048 / 4.096 / 4.096 / 8.192) waren nie gemessen. Neu ist die Tally Chute ein
Zehnt: sie nimmt nur, was der laufende Nachweis noch will, und verbraucht es. Jede Zahl ist etwa zwei Stunden einer
Referenzlinie der Age (SPEC §35), auf ganze Stapel gerundet. Belege aus dem Kopie-Boot der Review-Runde:

| Age | Zahl | Referenzlinie | Rechnung |
|---|---|---|---|
| Industrie | 384 Stahl | ein Improved Blast Furnace mit zwei Vorwärmern | Rezept `immersiveengineering:blastfurnace/steel` 1.200 Ticks [Dump]; `AdvBlastFurnaceLogic.getProcessSpeed` = 1 + je aktivem Vorwärmer 1, zwei Plätze [J] → 400 Ticks je Barren, 180/h, 2 h = 360 |
| Elektro | 512 Aluminium | ein Excavator auf Laterit, Brecher 2,5x | Mix `immersiveengineering:mineral/laterite` [Dump]: Bauxit 11,5 mB je Grab nach 5 % Fehlgrab; 800 Grabungen/h → 9.208 mB/h → 230 Barren/h, 2 h = 460 |
| Information | 1.024 Osmium | kein Excavator-Mix, kein Miner: Create-Bohrer oder Hand auf TFC-Adern, Reinigung 3x | Zensus 208 Chunks [M]: 6.367 Osmium-Erzblöcke (30,6 je Chunk), Mittel 23,7 mB → 0,71 Barren je Block; 1.024 = 1.440 Blöcke, das Osmium von etwa 47 Chunks. Dauer nicht gemessen (Bohrer-Bauten streuen) |
| Weltraum | 2.880 Desh | ein Digital Miner ohne Upgrades auf dem Mond, eine Injektionskammer (4x) | Zensus Mond 52 Chunks [M]: 2.730 Desh-Erz = 52,5 je Chunk (ein Minerfeld mit Radius 32 hält etwa 870); Miner 360 Blöcke/h, Injektion 1 Erz → 4 Splitter in 200 Ticks [Dump, J] → 1.440/h, 2 h = 2.880 |
| Quanten | 3.584 Calorit | ein Digital Miner auf der Venus, Dissolution (5x) | Zensus Venus 52 Chunks [M]: 1.992 Calorit-Erz = 38,3 je Chunk (etwa 630 je Minerfeld); 360 Blöcke/h × 5 = 1.800/h, 2 h = 3.600, auf 56 Stapel gerundet. Ein Washer schafft ohne Upgrades nur 72 Erz/h (1 mB/Tick, 1.000 mB je Erz): die 5x-Linie braucht Tempo-Upgrades oder mehrere Washer |

Strom: 2.880 Desh = 720 Erzblöcke × 0,96 M FE = 0,69 G FE Abbau über die zwei Stunden, also 4.800 FE/t oder 6 % einer
1-mB/t-Spaltanlage; vorher (36.000 FE/t, 7,2 M FE je Block) wären es 5,2 G FE gewesen.
