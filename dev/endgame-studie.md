# Endgame-Studie (E1A): Was andere Packs tun, was Firmament Ages vertiefen kann

Stand 2026-10-18. Kurzstudie, kein Umbau. Die Inhaltssperre (`dev/content-lock.md`) gilt: Vorschläge mit neuen Mods brauchen Felix' Freigabe.
Quellenlage: Websuche lieferte Versionsdaten (Links unten), aber kaum Packdetails. Packmechaniken in Tabelle 1 sind Fachwissen des Autors, nicht belegt (Spalte „Beleg“).

## Empfehlung (10 Zeilen)

1. **Vorschlag A, „Ages-Stern“ (Aufwand klein bis mittel, Wirkung hoch):** ein Endgame-Item aus einem Produkt jeder Age (nicht der Opfergaben, die stecken schon im Ultimate Singularity), z. B. je ein Spät-Item der Mod-Schlüssel (Mekanism, AE2, Ad Astra, Evolved Mekanism, Stargate, Draconic). Craft an einer neuen Altar-Station nach dem Finale. Vorbild: ATM Star. Nur firmages-core plus KubeJS, keine neue Mod.
2. **Vorschlag B, Wellen-Verteidigung „The Siege of Caelum“ (Aufwand mittel, Wirkung hoch):** nach dem Sieg über The Primordial belagern Wellen den Schrein; Preis sind Chaos-Rohstoffe oder Blessing-Stufen. Vorbild: Gateways to Eternity (nur Zusatz-Mod, 1.21.1 vorhanden) oder eigener Wellen-Code in `boss/`. Passt zur Schrein-Identität.
3. **Vorschlag C, „New Game Plus: Ages Rückblick“ (Aufwand mittel, Wirkung mittel bis hoch):** nach `finale_won` ein Rücksetzen der Team-Ages mit behaltenen Blessings; jede Age bekommt eine schwerere Variante (höhere Tally, Ring-Zusatzforderung). Nutzt `AgeSwap` und Stage-Dateien, kaum neue Inhalte.
4. Reihenfolge nach Wirkung je Aufwand: A, dann B, dann C. Zweite Origin-Phase (Idee 4) erst nach B, weil sie die Arena teilt.
5. Nicht empfohlen: Avaritia-Klone und Extended Crafting als „Unendlichkeitsschicht“ (Konflikt mit „ein Item pro Material“, Erzleiter, Kein-Kram-Regel); Blood Magic (nur inoffizieller Alpha-Port); GregTech (ersetzt Mekanism- und IE-Ketten, bricht die Age-Leiter).
6. Felix' Entscheidung nötig: Ob eine Nach-Finale-Schicht überhaupt gewollt ist (Pack endet bewusst mit The Primordial), und ob eine Mod-Ausnahme (Gateways to Eternity) in die Sperre darf.

## 1. Was andere Packs im Endgame tun

| Pack | Endgame-Mechanik | Idee für uns | Beleg |
|---|---|---|---|
| SevTech Ages | Letzte Age „Futuristic/Creative“, Abschluss durch Ultimate Crafting Table; Fortschritt per Advancements | Wir haben das Ages-Modell schon; Abschluss-Bau statt Boss wäre ein Gegenstück | [G-Portal](https://www.g-portal.com/wiki/en/minecraft-sevtech-ages/) |
| ATM 10 / „ATM Star“ | Endgame-Item am Runic Star Altar, 8 Komponenten aus Dutzenden Mods plus 3 ATM Alloys; Quest-Buch führt hin | Direktes Vorbild für den Ages-Stern (A) | [Sirius-Wiki](https://wiki.siriusmc.net/books/modpack-guides-and-tutorials/chapter/all-the-mods-10/export/html), [Akliz](https://www.akliz.net/blog/posts/exploring-all-the-mods-10) |
| Vault Hunters (3rd Edition) | Sammeln von 25 Artefakten für den Endvault, danach „Greed“ als Endlos-Loot | Unsere neun Relikte sind das gleiche Muster; offen: Endlos-Schleife danach (C) | [CurseForge](https://www.curseforge.com/minecraft/modpacks/vault-hunters-1-18-2) |
| GTNH | Sehr lange Tier-Leiter (LV bis UXV), Endgame Stargate, Fusion, Raumstationen | Stargate haben wir (Singularity); Tier-Tiefe ist der Unterschied, nicht übernehmbar | nicht belegt |
| Nomifactory | GregTech-CEu-Leiter bis Chaos-Drache und Avaritia-Endgegenstände | Chaos-Kette haben wir mit Draconic; zusätzlicher Endboss-Wiederholbarkeit vorbildlich | nicht belegt |
| Enigmatica (E2E, E9) | Schwerpunkt Quests und Skriptketten, Endgame Draconic und AE2; E9 mit EMC-Idee | Tally und Quest-Reward-Modell ähnlich | nicht belegt |
| Create: Astral | Kinetik bis Raumfahrt und Mythisches | Ähnlich unserer Space Age | nicht belegt |
| Prominence 2 | RPG-Progression, Bosse in Dimensionen | Bosse mit Dimension als Wiederholziel (B, 4) | nicht belegt |
| FTB Skies / Evolution | Himmelsinsel bzw. Weltprogression mit Quest-Gating, Endgame Draconic/AE2 | Wir decken das ab | nicht belegt |
| StoneBlock 4 | Technik- und Erzpack mit Quest-Endgame, Mod-Mix ähnlich | Kein neues Muster | nicht belegt |
| Divine Journey 2 | Dimensionspfad (Himmel, Twilight, Mystik) mit Bosskette | Boss-Kette nach dem Finale als Wiederholschleife | nicht belegt |

Muster über alle Packs: (a) ein sammelbares Mega-Item aus allen Spielbereichen, (b) ein Boss oder eine Schleife danach, (c) Tier-Tiefe statt neuer Welt. Firmament Ages hat (a) in Form des Ultimate Singularity und (b) mit The Primordial; es fehlt eine Schleife nach dem Sieg.

## 2. Mods und Mechaniken auf NeoForge 1.21.1

| Mod / Mechanik | 1.21.1 NeoForge | Passt zu uns | Quelle |
|---|---|---|---|
| Avaritia (Original) | kein Original-Port gefunden; Fanmods: AvaritiaNeo, Infinitum (WIP, Singularity-Rezepte), Re:Avaritia IO | Nein: Infinity-Items sprengen Erzleiter und „ein Item pro Material“ | [Infinitum](https://moddex.gg/mod/infinitum), [Re:Avaritia IO](https://www.modpackindex.com/mod/113310/reavaritia-io), [AvaritiaNeo](https://www.mado-hosting.com/minecraft-mods/avaritianeo) |
| Extended Crafting | ja, 7.0.8 (Mirror der Modrinth-Liste) | Technisch ja; große Raster-Rezepte wären eine eigene Endgame-Station, aber neue Mod | [boxtoplay](https://www.boxtoplay.com/en/minecraft-hosting/minecraft-server/modrinth-mods/extended-crafting-1817) |
| Gateways to Eternity | ja, 1.21.1 gelistet, zuletzt 2025-08 | Sehr gut für Wellen-Verteidigung (B), datengesteuert, KubeJS-fähig | [GDLauncher](https://gdlauncher.com/mods/curseforge/gateways-to-eternity) |
| GregTech CEu Modern | ja (1.21 / 1.21.1), Entwicklung zäh | Nein: eigene Tier-Leiter ersetzt unsere | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/gregtechceu-modern/files/5493709) |
| Compact Machines | nur „CompactMachinesPOR“ 0.9.1 beta gefunden, Herkunft ungeklärt | Optional als Taschenfabrik; nicht prüfbar | [mado](https://www.mado-hosting.com/minecraft-mods/compactmachinespor) |
| Dimensional Doors | ja, 6.1.2 (April 2026) | Taschendimension als Schrein-Arena denkbar; Raumsperre nötig | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/dimensionaldoors/files/7985315) |
| Ars Nouveau | ja, bis 5.13.3; Ars Ocultas 2.6.1 (Occultism-Brücke) | schon im Pack (Arcane); Endgame-Glyphen wären eine Magie-Schiene | [boxtoplay](https://www.boxtoplay.com/en/minecraft-hosting/minecraft-server/modrinth-mods/ars-nouveau-554), [Ars Ocultas](https://www.mado-hosting.com/minecraft-mods/ars-ocultas) |
| Occultism | im Pack; Endgame ist Dimensionsreise und Great-Spells | bereits Arcane Age | Pack-Kontext |
| Blood Magic | nur inoffizieller Alpha-Port 3.4.0-46 | Nein (Alpha, Felix akzeptiert Beta, aber Port ohne Autor) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/blood-magic-unofficial-port) |
| Botania (Gaia Guardian) | nur inoffizieller Port „Botania Revived“ (Mod-ID botania) | Gaia-Boss wäre ein Gegenstück zu The Primordial; Port-Risiko | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/botania-revived) |
| Eternal Starlight | ja, 0.9.0 (Dimension mit Bossen) | Zusatzdimension mit Bossen für Nach-Finale-Schleife; Dimension müsste an Stage hängen | [Modrinth](https://modrinth.com/mod/eternal-starlight), [skinmc](https://skinmc.net/project/eternal-starlight) |
| Ages-Stern (ATM-Star-Muster) | kein Mod nötig | eigener Altar via firmages-core | siehe 1 |

Nicht geprüft: Draconic-Evolution-Endgame-Erweiterungen, Mystical-Agradditions, Iron's Spells. Zu prüfen vor jeder Aufnahme: Mixins (`check_mixin_refs.py`), Age-Zuordnung (`derive_ages.py`), Inhaltssperre.

## 3. Vertiefungsideen (ohne die Age-Logik zu brechen)

Prinzip: alles nach `finale_won` oder an eine bestehende Age gebunden, nie ein neuer Pflichtweg; Technik-Weg bleibt Pflicht, Magie Bonus; Belohnungen sind echte Items und nie Chaos oder Opfergabe-Items (Regeln aus kontext.md §2).

| # | Idee | Kern | Aufwand | Wirkung | Risiko für Age-Logik |
|---|---|---|---|---|---|
| 1 | Ages-Stern | Item aus je einem Produkt der zehn Ages; Craft am „Altar der Zehn“ (Fusionskern-Rezept wie Ultimate Singularity); Lohn: kosmetische und nützliche Endgame-Items, keine Maschinen früherer Ages | klein bis mittel | hoch | niedrig: nur Items, die es gibt; Regel K-1 und Q-2 beachten |
| 2 | Wellen-Verteidigung des Schreins | „Siege of Caelum“: Wellen aus Origin-Mobs stürmen Plinth und Ringe; Teamweit; Skalierung mit Zahl der Ringe; Preis: Blessing-Shards, Chaos-Fragmente (nur Zuwachs, nicht mehr als der Weg) | mittel (eigener Code in `shrine/` oder Gateways to Eternity) | hoch | mittel: Chunk-Laden nur mit Team online beachten; Blessings dürfen Erzleiter nicht verschieben |
| 3 | Zweite Origin-Phase | nach Primordial neue Arena-Stufe mit Zusatzmechanik (z. B. Phasen nach Ring), freigeschaltet durch Ages-Stern | mittel bis hoch | mittel | niedrig; Arena teilt `origin/` und `boss/` (SPEC §16.4, §39) |
| 4 | New Game Plus / Age-Rückblick | nach `finale_won` Team-Ages zurücksetzen, Blessings behalten; schwerere Variante je Age (mehr Tally, Ring-Zusatzforderung, Mob-Leiter eine Stufe härter); Rückblick-Quest je Age | mittel | mittel bis hoch | mittel: Weltzustand (gebaute Maschinen) bleibt, Stage-Gate sperrt Items; Loot-Filter greift bereits |
| 5 | Prestige-Stufen | Zähler pro Team, jeder Durchgang schaltet Titel und Kosmetik (Hat, Banner, Himmel) frei, kein Machtgewinn | klein | niedrig bis mittel | sehr niedrig |
| 6 | Dimension nach dem Finale (Eternal Starlight oder Dimensional Doors) | Wiederholbares Bossziel hinter einem Stage-Gate `finale_won`; neue Mod nötig | mittel + Sperre | mittel | niedrig bei Stage-Gate; Inhaltssperre bricht |
| 7 | Endlos-Reaktor-Challenge | Reaktor als Dauerziel: Ranglisten-Quest „n Zyklen ohne Abschalten“, Lohn kosmetisch | klein | niedrig | niedrig |

## 4. Quellen

- ATM 10 Übersicht: https://wiki.siriusmc.net/books/modpack-guides-and-tutorials/chapter/all-the-mods-10/export/html und https://www.akliz.net/blog/posts/exploring-all-the-mods-10
- SevTech Ages: https://www.g-portal.com/wiki/en/minecraft-sevtech-ages/
- Vault Hunters: https://www.curseforge.com/minecraft/modpacks/vault-hunters-1-18-2
- Avaritia-Fans-Ports: https://moddex.gg/mod/infinitum, https://www.modpackindex.com/mod/113310/reavaritia-io, https://www.mado-hosting.com/minecraft-mods/avaritianeo
- Extended Crafting: https://www.boxtoplay.com/en/minecraft-hosting/minecraft-server/modrinth-mods/extended-crafting-1817
- Gateways to Eternity: https://gdlauncher.com/mods/curseforge/gateways-to-eternity
- GregTech CEu Modern: https://www.curseforge.com/minecraft/mc-mods/gregtechceu-modern/files/5493709
- Dimensional Doors: https://www.curseforge.com/minecraft/mc-mods/dimensionaldoors/files/7985315
- Blood Magic (inoffiziell): https://www.curseforge.com/minecraft/mc-mods/blood-magic-unofficial-port
- Botania Revived: https://www.curseforge.com/minecraft/mc-mods/botania-revived
- Eternal Starlight: https://modrinth.com/mod/eternal-starlight
- Ars Nouveau und Ars Ocultas: https://www.boxtoplay.com/en/minecraft-hosting/minecraft-server/modrinth-mods/ars-nouveau-554, https://www.mado-hosting.com/minecraft-mods/ars-ocultas
- Compact Machines (ungeklärt): https://www.mado-hosting.com/minecraft-mods/compactmachinespor

Offen und nicht verifiziert: Versionen stammen von Spiegelseiten, nicht von den Projektseiten; Pack-Mechaniken von GTNH, Nomifactory, Enigmatica, Create: Astral, Prominence, FTB, StoneBlock 4 und Divine Journey 2 sind ohne Beleg aus Fachwissen.
