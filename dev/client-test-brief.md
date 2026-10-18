# Client-Test

## 1.2.0 Checkliste (Runde G3, Pack 1.2.0, firmages-core 1.2.0, 2026-10-18)

Die einzige offene Client-Liste; alles bis 1.1.0 liegt im Archiv `dev/archive/client-test-brief-bis-1.1.0.md`.
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
