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
| Sonder | Laser 4.000 → 40.000; Teleporter 400 + 4/Block → 4.000 + 40/Block (40.000 zwischen Dimensionen); Digital Miner 400 → 36.000 (Abschnitt 5) |
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

**Vorher (H2A x10, Miner auf Mekanism-/IE-Standard) [B]:** Digital Miner 10.000 J/t × 80 Ticks = 320.000 FE je Block
= 13.333 FE/mB. FE je Barren: 3x 578 k, 4x 893 k (+55 %), 5x 1,43 M (+61 %): jede Stufe über 3x kostete mehr Strom
als ein zweiter Miner. Excavator Tempo 1: Mittel 13,5 k FE/mB; mit Energie-Upgrades 5x nur −1,7 % gegen 4x (reichste Ader +7,6 %).

**Jetzt:**

| Miner | Einstellung | Durchsatz | FE je Erz-mB |
|---|---|---|---|
| Digital Miner | 90.000 J/t (36.000 FE/t), 200 Ticks je Block | 360 Blöcke/h ohne, 3.600 mit 8 Tempo-Upgrades (dann 360.000 FE/t mit 8 Energie-Upgrades); 8.640 bzw. 86.400 mB/h | 7,2 M FE je Block bei gleich vielen Tempo- und Energie-Upgrades = 300.000 |
| IE Excavator | 4.096 FE/t, Tempo 0,5 (ein Grab je 90 Ticks), Ertrag 38.400 je Ader | 800 Grabungen/h; je Ader 108 bis 760 Erzstücke/h, Mittel 13,6 mB je Grab = 109 Barren/h bei 1x | Mittel 27.021 (16.384 reichste Ader) |

FE je Barren (Abbau + Verarbeitung) [B, `miner_ladder.py` auf dem Dump]:

| Route | 2,5x | 3x | 4x | 5x |
|---|---|---|---|---|
| Digital Miner, Mekanism mit denselben Upgrades | 12,01 M | 10,13 M (−15,6 %) | 8,06 M (−20,5 %) | 7,17 M (−11,1 %) |
| Excavator Mittel, Mekanism mit 8 Energie-Upgrades | 1,09 M | 0,91 M (−15,9 %) | 0,73 M (−20,0 %) | 0,66 M (−10,2 %) |
| Excavator reichste Ader, 8 Energie-Upgrades | 0,66 M | 0,56 M (−15,4 %) | 0,47 M (−16,8 %) | 0,44 M (−4,5 %) |
| Excavator Mittel, Mekanism ohne Upgrades | 1,09 M | 1,03 M (−4,8 %) | 1,24 M (+19,5 %) | 1,71 M (+38,3 %) |

Barren je Stunde aus einem Miner: Digital Miner mit 8 Tempo-Upgrades 86.400 mB/h → 2.592 (3x), 3.456 (4x), 4.320
(5x) Barren/h bei 360.000 FE/t; ohne Upgrades 259 / 346 / 432 Barren/h. Excavator Mittel 10.912 mB/h → 273 (2,5x),
327 (3x), 436 (4x), 546 (5x) Barren/h. Die Stufe hebt also bei gleichem Miner den Ertrag je Stunde um 33 % bzw.
25 % und senkt die FE je Barren; ein zweiter Miner kostet dagegen den vollen Abbaustrom.

Regel im Check: jede Stufe senkt die FE je Barren um mindestens 5 % für den Digital Miner und die mittlere
Excavator-Ader mit Energie-Upgrades. Ohne Energie-Upgrades lohnt sich hinter dem Excavator nur 3x (Zeile 4); das
Questbuch soll Energie-Upgrades ab der Informationszeit lehren (Paket A11, offener Punkt).

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
