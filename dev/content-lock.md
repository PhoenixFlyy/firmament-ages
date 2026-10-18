# Inhaltssperre 1.0

Felix hat den Inhalt von 1.0 am 2026-10-15 gesperrt. **Keine neuen Mods ab hier.** Was nicht in dieser Liste steht,
kommt nicht ins Pack, auch keine Bibliothek, die nicht eine der gelisteten Mods braucht. Ab jetzt gilt nur noch
Harmonie: Synergie, Balance, Quests und Belohnungen mit dem, was da ist. Umgesetzt in Pack 0.11.0 (Runde C1).

Quelle: die Vorauswahl (Abschnitt „Nehmen“ mit ihren Auflagen) und Felix' Antworten mit den Codes V, H und S.
Die Ages folgen `dev/age_map.toml`; abgeleitete Ages stehen im erzeugten Teil der Datei.

## Neue Mods (Runde C1)

| Age | Mod | Auflage |
|---|---|---|
| Dawn | A handful of acorns | Nüsse saisonal von TFC-Bäumen; Lange Astschere in der Bronzezeit |
| Dawn | KleeSlabs | keine |
| Dawn | Goblin Traders | Tauschware TFC-Waren nach Age, kein Schatzkarten-Tausch |
| Dawn | Alex's Mobs Continued (V1B) | nur Arten, die TFC fehlt (22), Spawns über TFC-Klima; alle anderen Gewicht 0 und versteckt |
| Stein (age_0) | Armor Statues | keine |
| Stein (age_0) | Banner Flags | nur Modrinth, fehlt im CurseForge-Zip |
| Bronze (age_1) | Connected Glass | keine |
| Bronze (age_1) | Advanced Chimneys | keine Rauchvergiftung; Emitter an TFC-Feuerstellen |
| Bronze (age_1) | To the Bat Poles! | Hebestange erst age_2; nur zusammen mit Advanced Chimneys |
| Bronze bis age_4 | Artifacts | acht Artefakte aus (Ewiges Steak und Rind, Pickaxe Heater, Digging Claws, Rooted Boots, Chorus Totem, Helium Flamingo, Universal Attractor); Verteilung per LootJS in TFC-Ruinen, Cold Forge, Netherfestung |
| Eisen (age_2) | Bibliocraft Legacy | Sitze, Tische, Regale, Lampen, Uhren, Prunk-Rüstungsständer, Werkzeugregal, Schwertsockel gesperrt |
| Eisen (age_2) | MmmMmmMmmMmm | keine |
| Arkan (age_3) | Ars Elemental, Ars Ocultas | Rüstungs-Upgrades aus Material statt Rüstung |
| Arkan (age_3) | Akashic Tome | Whitelist nur Handbücher |
| Arkan (age_3) | Enchantment Descriptions | keine |
| Industrie (age_4) | Create: Extra Gauges | keine |
| Elektrik (age_5) | AeroEngine | Flugkerosin aus IP-Kerosin, Raketen versteckt |
| Elektrik (age_5) | Building Gadgets 2 | Copy-Paste, Cut-Paste, Template Manager gesperrt; Tauschgadget verweigert TFC-Erze und Rohgestein |
| Elektrik (age_5) | Ars Elemancy | wie Ars Elemental |
| Information (age_6) | Ars Énergistique | ME Source Acceptor raus (Magie gibt kein FE) |
| Information (age_6) | Psi (V3B) | Programmier-Magie; Osmium-Teile, CAD-Assembler mit zwei Mekanism-Schaltkreisen |
| Singularität (age_9) | Draconic Additions (V2A) | nur Kuriositäten; 2.6.0.4 hat nur die drei Halsketten, Chaos-Ausrüstung versteckt |
| Client | 3D Skin Layers, Auroras, Cool Rain Reforged, BetterF3, Chat Heads, Big Sign Writer | Auroras: Polarlicht im Complementary-Shader aus; BetterF3 hält die Koordinaten gesperrt |
| Bibliotheken | ForgeEndertech, Bookshelf, Prickle, Codxlib | nur als Abhängigkeit |

## Ausnahmen nach der Sperre

| Datum | Mod | Version (Quelle) | Lizenz | Entscheidung |
|---|---|---|---|---|
| 2026-10-17 | JourneyMap | 1.21.1-6.0.9, `journeymap-neoforge-1.21.1-6.0.9.jar` (CurseForge 32274, Datei 8923668; Common Networking steckt im Jar) | All Rights Reserved; nur als CurseForge-Metadatei, Jar nie im Repo oder Upload-Zip | Felix M1A 2026-10-17: Minimap mit eigenem Theme „Firmament“, Karte ab der Eisenzeit (firmages-core `ui.mapFromAge`) |
| 2026-10-17 | JourneyMap Integration | 1.21.1-1.9.1, `jmi-neoforge-1.21.1-1.9.1.jar` (CurseForge 525447, Datei 9121304), nur Client | MIT | Felix M1A 2026-10-17: FTB-Claims auf der JourneyMap-Karte, FTB-Minimap aus |
| 2026-10-17 | Enhanced Falling Trees | 0.7.0-1.21.1+neoforge (Beta), `efallingtrees-0.7.0-1.21.1+neoforge.jar` (CurseForge 1078653, Datei 8408217), Client und Server; braucht Cloth Config (im Pack) | MIT | Felix T1A 2026-10-17: Baum kippt sichtbar, Stämme fallen nach dem Fall; TFC-Fällung aus |
| 2026-10-17 | Not Enough Animations | 1.12.6, `notenoughanimations-neoforge-1.12.6-mc1.21.1.jar` (Modrinth MPCX6s5C, Version VCuMsK45; CurseForge 433760, Datei 8925075 in `dev/cf/swap/`: CurseForge sperrt die API-Verteilung), nur Client | tr7zw Protective License (wie Skin Layers 3D; keine kommerzielle Nutzung); nur als Metadatei, Jar nie im Repo oder Upload-Zip | Felix G2 2026-10-17: Spieler-Animationen; Leiter, Kriechen, Essen und Trinken, Karte, Laterne, Boot, Pferd in der dritten Person; jeder Client rendert die Posen aller Spieler, darum sehen andere sie mit; Defaults der Mod (ruhig), keine Pack-Config |
| 2026-10-17 | Emotecraft | 2.4.12 für 1.21.1, `emotecraft-for-MC1.21.1-2.4.12-neoforge.jar` (CurseForge 403422 „Emotecraft (Forge)“, Datei 6941989), Client und Server; Player Animator 2.0.1 und bendy-lib 5.1 stecken im Jar | GPL-3.0 (Emotecraft), MIT (Player Animator), CC-BY-4.0 (bendy-lib) | Felix G2 2026-10-17: Spieler-Animationen; Winken und Emotes, der Server verteilt sie an alle Spieler in Sichtweite; Emote-Rad auf Y (`config/defaultoptions/keybindings.txt`), Rad beim ersten Start mit acht eingebauten Emotes belegt (`config/defaultoptions/extra/config/emotecraft.json`, Winken vorn); nicht nach Age gesperrt (kosmetisch) |
| 2026-10-18 | TabTPS | 1.3.25, `tabtps-neoforge-mc1.21.1-1.3.25.jar` (CurseForge 1567776, Datei 8211395), nur Server (Cloud, Adventure und Configurate stecken im Jar; der Client braucht nichts, `displayTest` IGNORE_ALL_VERSION) | MIT | Felix G3 2026-10-18 (T1A): Ping, TPS, MSPT und Speicher in der Fußzeile der Tab-Liste (`config/tabtps/display-configs/default.conf`), keine Kopfzeile, keine Koordinaten; Aktionsleiste und Bossleiste aus, jeder Spieler kann sie mit `/tabtps` einschalten |

## Runde-A-Korrekturen

- Carry On und MrCrayfish's Furniture: Refurbished sind raus. Framework bleibt (Goblin Traders braucht es).

## Eigener Inhalt in firmages-core

- **The Primordial**, der Endboss von The Origin (Zweig a1/boss, als c1/boss neu aufgesetzt).
- **Die acht Himmels-Ideen** aus `SPEC-himmel.md` (jetzt `mod/firmages-core/SPEC.md` §24 bis §33): Starfall (M10, in
  0.11.0), Sky Harvest (M11), Lens Crown (M12 in 0.11.0, versteckt bis M15), Wayshrines (M16, Wayshrine-Hälfte in
  0.11.0), Sky Anchors, Drifting Isles (statisch), Launch Complex, Origin Archipelago. Jedes Himmelsmaterial hat genau
  eine Quelle.

## Felix' Antworten

| Code | Antwort |
|---|---|
| V1B | Alex's Mobs Continued nur mit Arten, die TFC fehlt, und TFC-Klima-Spawns |
| V2A | Draconic Additions nur Kuriositäten, Chaos-Ausrüstung versteckt |
| V3B | Psi als Programmier-Magie in age_6 |
| V4A | Lens-Crown-Legierungen im Hauptweg: ab Arkan eine Legierung pro Age-Schlüsselrezept (M15) |
| V5A | keine Schnellreise bei Wayshrines; der Mekanism-Teleporter (age_7) ist der erste Teleport |
| S1A | Namen „Wayshrine Stone“ und „Shard of &lt;blessing&gt;“ |
| S2A | Legierungsnamen und Linsenfarben wie spezifiziert |
| S3A | Polarlicht jede klare Nacht im kalten Band, im Winter doppelte Chance |
| S4A | `shrine.blessings.everywhere` entfällt; Anker ersetzen ihn; WARN-Zeile für alte Configs |
| S5A | Drifting Isles nur statisch |
| S6B | Der Wind-Schrein ersetzt den Glacier Cairn: Insel-Schrein auf den Drifting Isles ab der Industriezeit |
| H1A | Alternator gibt FE nur im Verhältnis zur SU aus befeuerten Quellen |
| H2A | Mekanism-Maschinen-Verbrauch x10 |
| H3A | Theurgy-Erzfaktor 2,0 |
| H4A | Mystical-Agriculture-Metall-Essenz gibt TFC-Erzstücke |
| H5A | Tally-Nachweis in den Schrein-Opfergaben ab age_4 (spätere Runde) |

Dazu Felix' Notizen: AE2-Typen- und Kanal-Limit freier als Standard (wie andere Expert-Packs), Autocrafting bleibt
ab age_6; das Wireless Crafting Terminal darf früher kommen, mit sehr teurem Rezept. Quest-Belohnungen sind echte
Items, und jede Nicht-Deko-Mod bekommt einen Quest-Strang (beides spätere Runden).

## Nicht genommen

Die Abschnitte „Streichen“ (44 Mods) und „Später“ der Vorauswahl gelten weiter als Nein: Aquamirae, Distant Horizons
und Charta wären erst nach eigenem Test denkbar und brauchen dann eine neue Entscheidung von Felix. Astral Sorcery
entfällt, weil die Himmels-Kette das Thema abdeckt.
