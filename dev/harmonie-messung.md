# Harmonie-Messung (Paket A8): Referenzbauten, Generatoren, Verbraucher

Stand 2026-10-16, Branch `h3/measure` (auf dev b78760f, Pack und firmages-core 0.12.0). Paket A8 des Harmonie-Plans
(`harmonie-plan.md` im Scratchpad der Sitzung, Tabelle 2.1) und die offenen Messungen aus `dev/offene-punkte.md`.
Kürzel: [M] auf der Server-Kopie gemessen, [J] aus Jar oder Config gelesen (javap), [B] berechnet.

## 1. Werkzeug

- `dev/gen_reference_builds.py` schreibt je Referenzbau eine Strukturvorlage und eine Laufdatei nach `dev/reference/`
  (`--check` vergleicht, `--install <server>` legt sie als Welt-Datapack `firmages_reference` in eine Server-Kopie).
  Das Pack liefert davon nichts aus.
- `/firmages energy reference run <name>|all [Sekunden]` (firmages-core, nur mit `debug.allowSimulate`, SPEC §38.1):
  setzt den Bau auf einen eigenen, zwangsgeladenen Platz (x 30.000 + 160 je Lauf, y 200, z 30.000), füllt Brennstoff
  und Kühlmittel über die Capabilities, formt IE-Multiblöcke, startet Mekanism- und Draconic-Reaktoren, lässt ihn
  einlaufen (Aufwärmzeit je Bau) und misst 1.200 Ticks mit `/firmages energy audit`. Ergebnis: Log-Zeilen
  `[energy-reference <name>]` und `logs/firmages-energy-reference.json`. Jeder Generator speist Energie-Mülleimer
  (Trash Cans, nehmen jede FE-Menge): gemessen wird, was dort ankommt.
- Alle Läufe auf der Kopie `.wt/test-server-h3measure` (Ports 26300/26310, Seed 1001, neue Welt je Start, Ages per
  `firmages ages simulate grant age_0` bis `age_9`), synchronisiert mit `packwiz-installer-bootstrap -g -s server` aus
  `packwiz serve --port 8330`, das Branch-Jar darüberkopiert. Felix' Test-Server blieb aus und unberührt.

## 2. Ergebnis gegen Band 2.1

| Bau | Age | Gemessen [M] | Band 2.1 | Urteil |
|---|---|---|---|---|
| Create: Kessel Stufe 18, 18 Dampfmaschinen, 18 Alternatoren bei 256 U/min | age_4 | vor dem Fix 0,0 FE/t, danach **3.024,0 FE/t** | 1.000–3.024 | im Band (Fix §3.1) |
| Create: Kreativmotor 256 U/min an Alternator (steht für Wasserrad und Windmühle) | age_4 passiv | **0,0 FE/t** | ≤ 302 (10 %) | im Band |
| CDG Huge Diesel Engine an einem Alternator (224 U/min, 14.336 von 16.384 SU) | age_5 Nebenquelle | **147,0 FE/t**, 76 mB Diesel je 600 Ticks (0,127 mB/t) = 1,16 M FE je Eimer bei 87,5 % Last | ≤ 1,6 M FE/Eimer | im Band |
| IE Diesel Generator | age_5 | nicht live: geformt, 24.000 mB Diesel, Redstone-Modus auf „an“, bleibt aber `active=false` (§5) | 4.096 je Generator [J] | Config-Wert |
| Mek Gas-Burning Generator, eine Ethen-Linie (1 mB/t) | age_6 | **11.339,7 FE/t** | 10.000–20.000 | im Band |
| More Machine Large GBG, eine Ethen-Linie (1 mB/t) | age_6 | **11.280,0 FE/t** | 10.000–20.000 | im Band |
| Mek Spaltreaktor 1 mB/t + Turbine 11×11×18 (28 Blätter) | age_7 | Standard **78.711,4 FE/t**, nach Tuning **98.399,9 FE/t** (Dampf 24.990 mB/t, Fluss 24.812) | 50.000–120.000 | im Band |
| Mek Fusion, Injektion 20, luftgekühlt (passiv) | age_8 | Standard **3.952.901,9 FE/t**, nach Tuning **96.769,7 FE/t** | ≤ Spaltung | im Band nach Tuning |
| Mek Fusion, Injektion 20, wassergekühlt, Turbine 11×11×18 mit 12 Ventilen | age_8 | nach Tuning **841.139,4 FE/t** aus der Turbine (+ 47.635 FE/t Thermoelement-Anteil im Reaktorpuffer) | 200.000–1.000.000, ≥ 3 × Spaltung | im Band |
| DE-Reaktor, 4 Blöcke Brennstoff, Entnahme 2,0 M RF/t | age_9 | **1.090.601,5 FE/t netto** (2,0 M ab, 909.399 FE/t ins Feld; 5.245 °C, stabil) | 1–3 M | im Band |

Kriterien des Auftrags (nach Tuning):

- Spaltung ≥ 5 × age_6-Anlage: 98.400 / 11.280 (eine Ethen-Linie, gemessen) = **8,7 ×**; gegen die Bandobergrenze
  20.000 = 4,9 × (im eingeschwungenen Zustand 99.960 = 5,0 ×).
- Fusion aktiv ≥ 3 × Spaltung: 841.139 / 98.400 = **8,5 ×**. Fusion passiv ≤ Spaltung: 96.770 ≤ 98.400.
- DE-Reaktor 1–3 M FE/t: 1,09 M (halber Reaktor); ein voller Reaktor (8 Blöcke, 4 M Entnahme) liegt nach demselben
  Verhältnis bei etwa 2,2 M [B].
- Passive Quellen ≤ 10 % des Age-Bands: der unbefeuerte Alternator gibt 0 (H1A wirkt), Mekanisms passive Generatoren,
  IE-Wind-/Wasserrad, Thermoelektrik, Ad Astra Solar und More Machine Large Heat/Wind/Solar sind versteckt
  (`dev/age_map.toml` `[hidden]`, `disabled`). Die passive Fusion ist kein Freistrom (braucht D-T) und liegt ≤ Spaltung.

## 3. Fixes und Tuning

### 3.1 H1A-Fehler: Dampfmaschinen galten als unbefeuert

`compat/create/AlternatorFuel` las `PoweredShaftBlockEntity.enginePos` als absolute Position. Create 6.0.10 speichert
dort den Versatz Welle minus Maschine [J, javap `PoweredShaftBlockEntity.update`]. Die Maschine wurde nie gefunden,
jeder Dampf-Strang hatte den befeuerten Anteil 0: Alternatoren an einem befeuerten Kessel gaben **0 FE/t** [M]. Fix:
`shaft.getBlockPos().subtract(shaft.enginePos)`. Danach 3.024,0 FE/t [M] = 18 × 168 (480 × 256/256 × 0,35).

### 3.2 `config/Mekanism/generators.toml` (neu im Pack, volle Datei aus `dev/gen_machine_usage.py`)

| Wert | Standard | Neu | Grund |
|---|---|---|---|
| `fission_reactor.energyPerFissionFuel` | 1.000.000 J/mB | 1.250.000 | 1 mB/t: 78.711 → 98.400 FE/t [M]; 5 × Band-Obergrenze age_6 |
| `fusion_reactor.fuelEnergy` | 10.000.000 J/mB | 700.000 | passiv 3,95 M FE/t [M] = 40 × Spaltung |
| `fusion_reactor.thermocoupleEfficiency` | 0,05 | 0,017 | passiv ≤ Spaltung, aktiv ≈ 9 × Spaltung |

Mekanisms eigene Formeln [J, javap `FusionReactorMultiblockData`]: Gehäusetemperatur = Abbrand × fuelEnergy /
(Leitfähigkeit 0,1 + Wasseranteil 0,3); passiv = Gehäusetemperatur × 0,1 × Thermoelement; Dampf = Gehäusetemperatur
× 0,3 × 0,2 / 10. Daraus: passiv 20 × 700.000 × 0,017 / 2,5 = 95.200 FE/t, Turbine 20 × 700.000 × 0,75 × 0,2 / 2,5 =
840.000 FE/t (gemessen 96.770 und 841.139). Folge der kleineren fuelEnergy: die Fusion hält sich erst ab Injektion 20
wassergekühlt bzw. 10 luftgekühlt (Plasma ≥ 100 MK; Mekanisms GUI zeigt diese Mindest-Injektion selbst an). Bei
Injektion 98 (Maximum) gäbe die Turbine etwa 4,1 M FE/t [B]: mehr als ein halber DE-Reaktor, aber nur mit 98 mB/t D-T.
Brennstoff-Tabelle: D-T 43,19 M FE je Eimer (wassergekühlt mit Turbine), Leiter Kreosot < Diesel < Ethen < D-T hält
(`gen_fuels.py --check`). `miner_ladder.py --plant` jetzt 100.000.

### 3.3 Was sich als anders herausstellte als im Plan

- **Gas-Burning Generator:** kein fester 282-FE/t-Generator. Mekanism 10.7.19 und More Machine verbrennen je Tick
  `ceil(256 × Tankfüllung)` Brenntakte (der große zusätzlich × (1 + 63 × Füllung³)) [J, javap, M]: die Leistung folgt
  der Zufuhr, 11.280 FE je mB Ethen (= 11,28 M je Eimer, Tabelle). Ein voller Einzel-Generator gibt 72.192 FE/t, solange
  der Tank reicht [B]. Die age_6-Anlage ist darum die Ethen-Linie: eine Druckreaktionskammer ohne Upgrades macht
  1 mB/t (100 mB je 100 Ticks [J, Rezept `mekanism:reaction/substrate/water_hydrogen`]) = 11.280 FE/t; mit 8
  Tempo-Upgrades 10 mB/t (112.800 FE/t), dann aber auch 10 × Biokraftstoff (4 je Sekunde) und 1.000 mB/t Wasserstoff.
  Offener Punkt: ob Ethen mit vollen Upgrades zu stark ist (Abschnitt 6).
- **Turbine:** Mekanism verlangt (Breite − 3) / 2 ≥ (Rotorhöhe + 2) / 4 [J, javap `TurbineValidator`]; 28 Blätter
  (volle 10 J je mB Dampf) brauchen 14 Rotoren und damit 11 × 11 × 18. Eine 9 × 9 × 17 hält 13 Rotoren (26 Blätter,
  9,29 J/mB, 93 %).
- **Create-Kessel:** eine Dampfmaschine mit voller Effizienz dreht 64 U/min mit 256 SU je U/min (16.384 SU) [M]; 18
  Alternatoren bei 256 U/min nehmen genau die 294.912 SU von 18 Maschinen.

## 4. `balance.feCap` auf Mekanisms Energieweg (SPEC §37)

Copy-Config `feCap = ["mekanismgenerators:gas_burning_generator=100"]`, Neustart [M]:

| Weg | ohne Deckel | mit Deckel 100 |
|---|---|---|
| GBG → Energie-Mülleimer (FE) | 11.339,7 FE/t | **100,0 FE/t** |
| GBG → Mek Basic Energy Cube (Mekanisms strict energy, `MekEnergyMeter`) | 564,0 FE/t | **100,0 FE/t** |

Der Deckel greift auf beiden Wegen; kein Fix nötig. Nebenbefund: ein Mekanism-Block aus einer Strukturvorlage
(`/place template`, Strukturblock) hat keine Seitenkonfiguration und damit keine seitliche Energie-Capability [M,
`capcheck`]; von Hand oder vom Fake-Spieler gesetzt hat er sie. Spieler betrifft das nicht (Mekanism-Blöcke kommen nie
aus Vorlagen des Packs).

## 5. Verbraucher

Die Bauten `mek_purification`, `mek_purification_upgraded` (8 Tempo, 8 Energie), `mek_injection`, `mek_dissolution`
liegen in `dev/reference/`, live aber ohne Ergebnis: die vom Fake-Spieler gesetzten Kammern nahmen weder die
TFC-Erzstücke (`tfc:ore/rich_hematite`, 0 von 64) noch Energie über die Capability, also 0 FE/t [M]. IE Crusher, Arc
Furnace, Excavator, SPS und der voll aufgerüstete Digital Miner sind nicht gebaut. Ihre Verbräuche bleiben die
Config-Werte [J] (`machine-usage.toml` ×10, 282 Werte auf der Kopie zurückgelesen, Runde C2; Digital Miner voll
aufgerüstet 48.000 FE/t [B], `miner_ladder.py`): Purification 800, Injection und Dissolution 1.600 FE/t, IE-Excavator
4.096 FE/t.

## 5a. Absturz nach `/reload` (Nebenbefund)

Kopie-Start 7, nach einem `/reload` und mehreren Age-Freischaltungen: beim Lauf `mek_injection` (Erzstück in eine
Injektionskammer) `java.lang.StackOverflowError` im Server-Tick: TFC `HeatDefinition.matches` → NeoForge
`CompoundIngredient.test` → `Ingredient.getItems` → `new ItemStack` → TFC `HeatCapability.getDefinition` → wieder
dieselbe Wärme-Definition (Rekursion, bevor das Zutaten-Array fertig ist). Crash-Report
`crash-2026-10-16_11.41.41-server.txt` (Kopie, Inhalt im Report dieses Zweigs zitiert). Derselbe Lauf auf Kopie-Start 8
ohne `/reload` stürzte nicht ab. Welche Wärme-Definition (eine mit zusammengesetzter Zutat aus Tags) es ist, ist offen.

## 6. Weitere Messungen dieser Runde (Kurzfassung, Belege `dev/poc-results.md`, Runde H3)

- Sturmstab: `sky storm forecast` (Wiederholung von TFCs Wetterzyklus aus dem Klimamodell) 19,8 Sturmglas je Stab und
  echter Stunde über die nächsten 5 Stunden, 19,3 über 100 Stunden; acht Stäbe über zwei gesprintete Stunden 4,875
  Einschläge je Stab gegen 4,9 vorhergesagt. Ohne Spieler online steht TFCs Kalender und damit das Wetter.
- Polarlicht-Band: mit `spawn_offset` 2,5 °C 14 von 14 geprüften Seeds im Umkreis von 2.000 Blöcken (vorher 4 von 20
  darüber).
- Wayshrine-Dichte: Abstand je Typ, 21 von 30 Seeds mit 7 bis 11 (72/27 für alle: 14); 16 von 20 ist über den Abstand
  nicht erreichbar.
- The Primordial: Bot-Kämpfe 5,83 / 4,77 / 4,60 min, Modell mit den gemessenen Eingaben +7,9 / +2,6 / +7,1 %.

## 7. Offen

- IE Diesel Generator live: geformt per `createStructure`, Tank voll, Redstone-Modus „an ohne Signal“ gesetzt,
  Ausgänge belegt; der Generator bleibt `active=false`. Der Wert 4.096 FE/t bleibt Config [J]. Nächster Schritt: der
  Generator im Client von Hand gebaut, `/firmages energy audit 8 10` daneben.
- Ethen mit vollen Upgrades: eine voll aufgerüstete Druckreaktionskammer liefert Brennstoff für 112.800 FE/t (so viel wie
  die Spaltanlage); der Aufwand dafür (4 Biokraftstoff je Sekunde, 1.000 mB/t Wasserstoff) ist hoch. Gegen Felix'
  Regel „jede Stufe lohnt sich“ prüfen, wenn Spieler es tun.
