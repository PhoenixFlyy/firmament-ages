# Client-Test

## 1.4.0 Checkliste (Pack 1.4.0, firmages-core 1.4.0, 2026-10-18)

Die neueste offene Liste; die 1.3.0- und 1.2.0-Zeilen darunter gelten weiter. Neues Profil aus
`dev/exports/FirmamentAges-1.4.0.mrpack` (oder der CurseForge-Zip), Server aus dem Ordner `FirmamentAges-Server-1.4.0`
mit einer frischen Welt; Client und Server brauchen firmages-core 1.4.0.

| # | Was | Wie | Erwartet |
|---|---|---|---|
| 1 | ESC-Menü | im Spiel ESC | Welt sichtbar hinter den Knöpfen, kein Firmament-Hintergrund |
| 2 | Zoom in Dawn | frische Welt, in Dawn C halten | Ok Zoomer zoomt; Alt+C öffnet das Ars-Buch erst in seiner Age, Alt+P Psi |
| 3 | Dawn-Questkette | Dawn-Kapitel durchspielen | Sammelkette optional, Hunter vor Shelter, „And I would walk 500 miles“ zählt 500 Blöcke ohne Sprint, Thatch gibt 28 Thatch, „Knife and Hammer“ verlangt Messer- und Hammerkopf |
| 4 | Arena-Look | `/firmages origin tp @s force` (alte Welt vorher `/firmages origin rebuild`) | Stein, Tiefenschiefer, Moos und Purpur, Froschlichter; Jade zeigt „Unbreakable“; Wither und Kolben bewegen nichts |

## 1.3.0 Checkliste (Runde G4, Pack 1.3.0, firmages-core 1.3.0, 2026-10-18)

Gilt weiter neben der 1.4.0-Liste; die 1.2.0-Zeilen darunter gelten auch. Neues Profil aus
`dev/exports/FirmamentAges-1.3.0.mrpack` (oder der CurseForge-Zip), Server aus dem Ordner `FirmamentAges-Server-1.3.0`
mit einer frischen Welt; Client und Server brauchen firmages-core 1.3.0. Ages per `/firmages stage grant @s age_N`,
The Origin per `/firmages origin tp @s force`. Server-Teil auf einer Kopie geprüft (`dev/poc-results.md`, „1.3.0 round G4“).

| # | Was | Wie | Erwartet |
|---|---|---|---|
| 1 | Ankunft und Aufstieg | `origin tp`, von der Plattform (y 34) den Aufstieg hoch | Weg mit drei Läufen, zwei Brücken, neun Vitrinen, ohne Sprung begehbar bis zum Bogen am Südtor |
| 2 | Arena als Gelände | über Rand, Terrassen, Ring, Gräben, Brücken, Senke laufen | keine Lücke, kein Erstickungsblock, Kuppeln und Podeste mit Altären sichtbar |
| 3 | Altar-Texturen | einen Altar ansehen, dann (leere Hand, 3 s) entzünden | nachtblau mit Gold und Windrosen, entzündet animiert |
| 4 | Himmel hinter der Welt | in der Origin nach oben und auf Böden und Treppen sehen, Phase 1 bis 3 | Sterne, Aurora, Finsternis nur am Himmel, nie über Böden oder Wänden; mit Shaderpack nur die Nebelfarbe |
| 5 | Blitze | Kampf starten (`origin fight`) | Blitze über der Arena, dichter je Phase, kein Schaden, kein Feuer |
| 6 | Ring mit zehn Shards | Boss ansehen | zehn Segmente drehen sich, feuern nacheinander je ein Age-Geschoss mit eigenem Sprite |
| 7 | Geschosse | jedem Geschoss ausweichen und eins treffen lassen | Wirkung je Age (Brocken, Splitter, Lanze, Kette, Schwerkraft, Frost, in Phase 3 Schwarzes Loch), Treffer schmerzen spürbar |
| 8 | Nahkampf | näher als 4 Blöcke an den Ring | alle 2 s Schaden durch die Ringkante; von den Terrassen aus (HOVER 7) noch erreichbar? |
| 9 | Tempo und Dauer | allein mit Altären kämpfen, Zeit stoppen | Boss treibt schneller, springt in Phase 3 hinter Dich; etwa 8 min allein |
| 10 | Dawn: Icons und Abhängigkeiten | Dawn-Kapitel öffnen | „Stone Tools“ prüft den Axtkopf, „Thatch“ wartet auf das Messer, „By Hand“ fehlt (in „Hands and Keys“), „The First Spark“ wartet auf „You Wake“ |
| 11 | Stone Age: Icons und Abhängigkeiten | Steinzeit-Kapitel öffnen | Stamm Ton, Grubenofen, Formen vor den drei Strängen, Werkbank in „First Metal“; kein Icon zeigt ein späteres Item |
| 12 | Schrein-Ziel-Quest je Age | je Age die Ziel-Quest am Schrein lesen und erfüllen | drei Aufgaben (Ring beobachten, Opfergabe im Besitz, nächste Age), drei Seiten Text; keine „Raise …“-Quests, Bauanleitung in der Straßenmarkierung von The Firmament |

## 1.2.0 Checkliste (Runde G3, Pack 1.2.0, firmages-core 1.2.0, 2026-10-18)

Gilt weiter neben der 1.3.0-Liste; alles bis 1.1.0 liegt im Archiv `dev/archive/client-test-brief-bis-1.1.0.md`.
Neues Profil aus `dev/exports/FirmamentAges-1.2.0.mrpack` (oder der CurseForge-Zip), Server aus dem Ordner
`FirmamentAges-Server-1.2.0` mit einer frischen Welt; Client und Server brauchen firmages-core 1.2.0. Survival ohne
Operator-Wirkung, wo nicht anders genannt; Ages per `/firmages stage grant @s age_N` (als Operator). Server-Teil auf
einer Kopie geprüft (`dev/poc-results.md`, Abschnitt „1.2.0 round G3“).

| # | Was | Wie | Erwartet |
|---|---|---|---|
| 1 | Arena | `age_9`, `/firmages origin tp` in einer neuen Welt | Ankunftsplattform im Süden mit Tor, Bogen, Treppe und neun Vitrinen mit Relikten; Arena Radius 60 mit neun Age-Ringen, Rand über der Leere |
| 2 | Altäre | Bosskampf (`/firmages origin` Kampf), Phase 2 | Schild hält jeden Treffer ab; Altar mit leerer Hand 3 s kanalisieren (Glyphen, Licht, Summen), alle vier: Schild fällt; Boss trifft alle 20 s einen leuchtenden Altar per Blitz |
| 3 | Kampfgefühl | ein ganzer Kampf allein und zu zweit | Altar-Rückmeldung, Treffer am Schild, Enrage erst nach 12 min (14 mit Sternenfall) |
| 4 | Himmel je Phase | Kampf ohne und mit Shaderpack | Phase 1 Sterne, Phase 2 Aurora, Phase 3 Finsternis mit rotem Horizont und kippendem Sternbild, nach dem Sieg Morgendämmerung; Übergänge weich; mit Iris nur die Nebelfarbe |
| 5 | Origin ohne Monster | eine Nacht in The Origin, Weg zur Arena | keine Endermen oder andere Monster außer dem Boss und seinen Beschwörungen |
| 6 | Insel-Ruine | `age_4`, eine Drifting Isle (`/firmages locate`) | Ruine mit Truhe und vier Wind-Siegeln; das vom Wind gezeichnete Siegel zuerst, dann im Uhrzeigersinn öffnet den Deckel, ein falsches löscht alle; vor `age_4` keine Antwort: ein Siegel tut dann nichts (nur Klopfen) |
| 7 | Meerestiere | Ozean in Dawn, Bronze- und Eisenzeit | Rippenqualle und Blobfisch ab Dawn, Mimik-Oktopus und Pottwal ab Bronze, Hammerhai, Kragenhai, Riesenkalmar ab Eisen; Modelle und Geräusche stimmen |
| 8 | Wracks und versunkene Ruinen | `/locate structure firmages:ocean_wreck` und `firmages:ocean_ruin`, hintauchen | Wrack oder Ruine am Meeresgrund, Truhe nur mit Items der eigenen Age |
| 9 | Aquaculture 2 | angeln, Fische ansehen, Filet garen | Aquaculture-Fische mit TFC-Verfall, Filet über TFC-Hitze; Kabeljau, Lachs und die anderen Doppel nur von TFC; Haken und Eisenrute erst ab Eisenzeit sichtbar |
| 10 | Nächte je Age | nachts draußen in Dawn, Bronze-, Arkan- und Elektrozeit | Monster je Age zäher, ab Bronze manche mit Rüstung einer Age zurück, später mehr Monster; keine Änderung am Tag und an Spawnern |
| 11 | Blutmond | `age_2`: `/ec lunarForecast` und eine Blutmondnacht; `age_5` | Blutmonde erst ab der Eisenzeit und seltener als bisher (in `age_2` `/ec lunarForecast` ungefähr 40 % der Blutmonde von 1.1.0); Super-Blutmond erst ab der Elektrozeit |
| 12 | Cave Dweller | Dawn und Steinzeit tief unter Tage | Cave Dweller seltener als in 1.1.0, ab der Eisenzeit nie |
| 13 | Apotheosis-Weltstufe | Arkanzeit: Apotheosis-Weltstufenfenster | Stufe Frontier gesetzt (ab Elektro Ascent, Raumzeit Summit, Singularität Pinnacle); bewusste Wahl offen: die Stufe lässt sich im Fenster von Hand ändern |
| 14 | Monster-Ausrüstung | Bronzezeit-Nacht, gerüstete Zombies suchen | nur Kupferteile (eine Age zurück), keine späteren Metalle |
| 15 | Karte auf M | Eisenzeit Survival und Kreativ, M zweimal | Vollkarte öffnet und bleibt offen (kein Info-Fenster), zweites M schließt |
| 16 | Radar | Eisenzeit, Tiere, Mobs, zweiter Spieler | Minimap und Vollkarte zeigen alle samt Spielernamen; Teammitglieder weltweit |
| 17 | Something Unknown | Bronzezeit, Mekanism-Item in Truhe, am Boden (Jade), im Item-Rahmen, `[item]` im Chat | überall „Something Unknown“, keine Mod-Zeile; Kreativ zeigt Namen |
| 18 | TabTPS | Tab halten | Fußzeile Ping, TPS, MSPT, Speicher; keine Koordinaten |
| 19 | Menüs | Einzelspieler, Mehrspieler, Direktverbindung, Welt erstellen, Optionen samt Unterseiten, Ressourcenpakete, Pause, Laden | Sternbild-Hintergrund, Goldrahmen-Knöpfe, kein Logo; Mod-Fenster-Knöpfe ansehen (auch Goldrahmen) |
| 20 | Resourcepacks | neues Profil, Optionen, Ressourcenpakete | aktiv: Mod Resources, Moonlight, AFC-Compat (2), Firmalife, TFC-ified Supplementaries, Darker Ropes |
| 21 | Dusty Decorations | neues Profil, erster Start | kein „Updating to 2.0“-Fenster |
| 22 | Regression Titel und Laden | neues Profil starten | Titelbild mit Wortmarke wie 1.1.0, frühes Ladefenster unverändert, Shader-Voreinstellung aktiv |
| 23 | Regression Kartentaste | Dawn und Steinzeit: M, L, F3 | vor der Eisenzeit öffnet M nichts, L nichts, F3 zeigt `???` (wie 1.1.0) |
| 24 | Nächte, Elektrozeit | `age_5`, nachts draußen: Zombie ansehen (Lebenspunkte, z. B. mit Jade im Kreativmodus oder `/data`); `/firmages selftest server`; nach einem Age-Grant `/apoth` | Zombie nachts ca. 34 HP mit Schwarzstahl-Teilen, derselbe Zombie am Tag 20 HP; `nights_table` PASS; `/apoth` zeigt die Stufe laut Tabelle (`age_3` = Frontier) |
