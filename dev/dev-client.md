# Dev-Client: Firmament Ages in Prism

Stand 2026-09-30. Die Prism-Instanz **Firmament Ages Dev** liegt fertig im portablen Prism unter `tools/PrismLauncher/instances/FirmamentAgesDev/`. Sie ist nicht in Git (`tools/` steht in `.gitignore`).

Was die Instanz schon mitbringt:

- Minecraft 1.21.1, NeoForge 21.1.252, LWJGL 3.3.3 (`mmc-pack.json`).
- Java: `D:/Minecraft/MinecraftModServer/FirmamentAges/tools/jdk-21/bin/javaw.exe` (Temurin 21), fest eingetragen. Das Java 25 aus dem PATH wird nicht benutzt.
- RAM: min 4 GB, max 10 GB.
- Pre-Launch-Befehl: `"$INST_JAVA" -jar packwiz-installer-bootstrap.jar http://localhost:8080/pack.toml`. Vor jedem Start zieht er Mods, Configs und Skripte aus `packwiz serve`. Die Bootstrap-Jar liegt im `minecraft/`-Ordner der Instanz.
- `tools/PrismLauncher/prismlauncher.cfg` setzt Instanz-Ordner und Java-Pfad. Accounts stehen dort nicht drin.

## Start

1. **Pack ausliefern.** `dev\serve.bat` doppelklicken, oder im Repo `tools\packwiz.exe serve` ausführen. Das Fenster muss offen bleiben. Prüfen: http://localhost:8080/pack.toml zeigt `name = "Firmament Ages"`.
2. **Test-Server starten:** die Befehle stehen unten in „Age-Übergang testen“, Schritte 1 und 2 (Sync, Start mit Temurin 21, `whitelist add` und `op`). Ohne OP gehen `/stage` und `/fa_reconcile` nicht.
3. **Prism starten:** `tools\PrismLauncher\prismlauncher.exe`.
4. **Einloggen:** oben rechts *Accounts → Manage Accounts → Add Microsoft*. Den Login machst Du selbst im Browser. Zugangsdaten gehören nie in eine Datei im Repo oder in `tools/`. Prism speichert nur das Token in `tools/PrismLauncher/accounts.json`; die Datei bleibt lokal und ist über `tools/` aus Git raus.
5. **Instanz starten:** *Firmament Ages Dev* doppelklicken. Zuerst läuft das packwiz-Fenster (Sync), dann startet das Spiel. Beim ersten Mal lädt Prism Minecraft, NeoForge und Bibliotheken nach. Im Multiplayer `localhost` verbinden.

Wenn der Sync fehlschlägt: Läuft `packwiz serve` noch? Ein Klick auf *Launch* ohne laufendes serve bricht ab. Zum Offline-Testen den Pre-Launch-Befehl unter *Edit → Settings → Custom commands* kurz leeren.

## Befehle für Stages

In den Beispielen steht `@s` für Dich selbst. Für andere Spieler den Namen einsetzen.

| Zweck | Befehl |
|---|---|
| Stage vergeben | `/stage grant @s age_1` |
| Stage entziehen | `/stage revoke @s age_1` |
| Stages ansehen | `/stage list @s` oder `/fa_stages` |
| Hilfs-Stages (Karte, Mob-Leiter, Stationen) nachziehen | `/fa_reconcile` |
| Blöcke zählen (geladene Chunks) | `/fa_scan 48 tfc:ore/` |
| Stage-Dateien neu laden | `/progressivestages reload` |

Die Ages bauen aufeinander auf: `dawn → age_0 → age_1 (Bronze) → age_2 (Iron) → …`. Fehlt die vorherige Age, fragt `/stage grant` nach; den Befehl dann ein zweites Mal senden. Sauberer ist es, die Ages der Reihe nach zu vergeben. Zurück auf Anfang: die höheren Ages von oben nach unten mit `/stage revoke` entziehen, dann `/fa_reconcile`.

Jeder Befehl wurde aus Doku, Quellcode oder `server/README.md` abgeleitet. Im Spiel gelaufen ist noch keiner. Weicht die Syntax ab, bitte in `dev/poc-checklist.md` korrigieren.

## Client-Checkliste

Kurzfassung der Punkte aus `dev/poc-checklist.md`, Abschnitt C, die einen Client brauchen. Details und die restlichen Punkte stehen dort.

### 1. EMI blendet pro Age aus (C14, C19)

1. Neuer Spieler, Stand `dawn`. In EMI `create:deployer` und `create:zinc_ingot` suchen, auch in den JEI-Plugin-Kategorien.
   Erwartung: beide unsichtbar.
2. `/stage grant @s age_0`, `/stage grant @s age_1`, `/stage grant @s age_2`.
   Erwartung: der Deployer taucht ohne Relog auf; `create:zinc_ingot` bleibt versteckt (Vereinheitlichung).
3. `/stage revoke @s age_2`.
   Erwartung: der Deployer verschwindet wieder.
4. In EMI `c:plates/iron` suchen.
   Erwartung: nur `tfc:metal/sheet/wrought_iron`, kein `create:iron_sheet`.

### 2. Quest-Fenster auf K ohne Buch (C10)

1. Frische Instanz (oder `minecraft/options.txt` löschen, dann greift `config/defaultoptions/keybindings.txt`). Taste **K** drücken.
   Erwartung: das Questfenster öffnet sich.
2. In EMI `ftbquests:book` suchen.
   Erwartung: kein Buch, kein Rezept.
3. Unter *Options → Controls* nach Tastenkonflikten auf K schauen (`config-todo.md` Nr. 21).

### 3. Erz-Tarnung (C3)

1. Stand `dawn`. Im Creative-Modus (`/gamemode creative`) eine Kassiterit-Ader in Granit oder Basalt suchen, z. B. mit TFCGenViewer oder `/fa_scan 48 tfc:ore/`.
   Erwartung: die Ader sieht aus wie Rohgestein. Jade zeigt Gestein. Ein Kies-Deposit sieht aus wie Kies.
2. `/gamemode survival`, einen Erzblock abbauen.
   Erwartung: es fällt loser Stein.
3. `/stage grant @s age_0`, `/stage grant @s age_1`.
   Erwartung: echtes Erz ohne Relog, Drop ist Erz.
4. `/stage revoke @s age_1`.
   Erwartung: die Ader ist wieder getarnt.
5. Leistung: `/spark profiler start`, mit 2 Spielern über 3 Adern fliegen, `/spark profiler stop`. Mit der Basis aus A14 vergleichen.

### 4. FTB-Chunks-Karte erst ab Iron Age (C2)

1. Stand `age_1` (`/stage grant @s age_0`, `/stage grant @s age_1`). Taste **M** drücken.
   Erwartung: keine Karte, keine Minimap.
2. `/stage grant @s age_2`.
   Erwartung: `grants.js` vergibt `ftbchunks_mapping`; `/fa_stages` zeigt es. M öffnet die Karte.
3. `/stage revoke @s age_2`, dann `/fa_reconcile`.
   Erwartung: `ftbchunks_mapping` ist wieder weg, die Karte ist gesperrt.

### 5. Create-Andesit-Stufe in der Bronze Age (C6)

1. Stand `age_0`. In EMI die Andesit-Legierung und die Create-Presse ansehen.
   Erwartung: gesperrt.
2. `/stage grant @s age_1`.
   Erwartung: Andesit-Legierung aus Zink-Nugget und TFC-Andesit-Cobble craftbar. Presse, Mixer, Lüfter und Säge bauen sich mit Bronzeblech, Eisen ist nicht nötig. Die Presse macht aus einem Doppelbarren ein Blech. Waschen eines Deposits geht. Der Mühlstein kopiert die Quern-Rezepte.
3. `create:crushing_wheel` und `create:deployer` craften versuchen.
   Erwartung: gesperrt bis `age_2`. Nach `/stage grant @s age_2` craftbar.
4. EMI zeigt in keiner Age `create:iron_sheet`.

### 6. Age-Wechsel-Titel (C20)

Seit firmages-core 0.3.0 kündigt die Mod jede Age an (kurze Zeremonie bei `/stage grant` und Quests, volle Zeremonie am Schrein, Abschnitt 7). Das KubeJS-Feuerwerk gibt es für Ages nicht mehr.

1. Einen zweiten Account (oder Freund) online haben, wenn möglich.
2. `/stage grant @s age_0`, dann `/stage grant @s age_1`.
   Erwartung bei allen Online-Spielern: genau ein Titel „The Bronze Age dawns“, Untertitel und Chatzeile „Caelum: A fire that keeps its shape. I see you.“, ein Klang, kurz gefärbter Himmel; dazu die Chatzeile aus `unlock_message`. Etwa 3 s später friert der Server kurz ein (Reload), die Aktionsleiste zeigt „The world realigns...“ bis er weiterläuft.
3. `/stage grant @s age_2`.
   Erwartung: Titel „The Iron Age dawns“, Chatzeile „the map opens and iron veins become visible.“
4. Beim Entziehen (`/stage revoke @s age_2`) darf kein Titel kommen. Wenn doch, notieren.
5. Nur ansehen, ohne Stage: `/firmages ceremony preview age_3 full` (oder `short`).

### 7. Schrein: der Age-Übergang selbst (M4, m7)

Kurzfassung mit den genauen Chat-Texten. Der ausführliche Ablauf mit Server-Start, Freischalten und Zurücksetzen steht unten unter „Age-Übergang testen“.

Vorbereitung: Welt im Stand `age_0` (`/stage grant @s age_0`). Im Creative-Modus geht es schneller; die Items liegen im Tab *Functional Blocks* (Shrine Heart, Offering Plinth). Im Survival: zwei Hearth Idols brennen, eins mit 4 Bruchstein und Holzkohle zum Shrine Heart, eins zum Hearthstone; der Sockel sind 4 Bruchstein.

1. **Herz setzen.** Shrine Heart auf den Boden stellen. Rechtsklick mit leerer Hand aufs Herz.
   Erwartung: Chat „Caelum, the Firmament...“ und „Caelum does not dwell in ruins: the Hearth Circle is not complete (1 of 22 blocks)“; ein Geisterbild zeigt die fehlenden Blöcke. **Bitte prüfen:** sitzt das Geisterbild genau um das Herz (nicht einen Block zu hoch oder zu tief)?
2. **Herdkreis bauen** (5x5, Herz in der Mitte): 8 TFC-Bruchstein direkt um das Herz, an den 4 Ecken des 5x5-Quadrats je 2 Holzstämme übereinander, darauf je ein Stroh-Block (`tfc:thatch`), und ein Offering Plinth zwei Blöcke vor dem Herz in der Mitte einer Seite.
   Erwartung: Rechtsklick aufs Herz sagt „Lay Hearthstone on its plinth first.“; über dem Herz fliegen Verzauberungs-Partikel (bereit). `/firmages shrine status` zeigt „Ring 0 ... complete“.
3. **Opfern.** Mit dem Hearthstone in der Hand den Sockel rechtsklicken.
   Erwartung: der Hearthstone liegt drehend auf dem Sockel, Chat „Hearthstone rests on the plinth...“ und der Hinweis „The heart is cold. Kindle it with a firestarter.“ Ein falsches Item wird mit „Caelum asks for Hearthstone on this plinth.“ abgelehnt. Schleichen + Rechtsklick mit leerer Hand holt das Opfer zurück.
4. **Ritus.** Mit dem Feuerstarter (oder Feuerstein und Stahl) aufs Herz rechtsklicken.
   Erwartung: Flammen auf dem Herz, Chat „The heart is kindled. Caelum watches.“
5. **Beten.** Schleichen und Rechtsklick mit leerer Hand auf das Herz gedrückt halten.
   Erwartung: Fortschrittsbalken in der Aktionsleiste, nach etwa 10 s (zu zweit 5 s, mindestens 4 s) antwortet Caelum: Musik aus, Klang, Partikel ziehen zum Herz, ein Lichtstrahl in Bernstein steigt auf, der Himmel färbt sich orange, dann Titel „The Bronze Age dawns“ mit „Caelum: A fire that keeps its shape. I see you.“ und eine Chatzeile zum Segen „Hearthward“. Nach etwa 3 s friert der Server für den Reload ein, die Zeremonie und „The world realigns...“ laufen weiter.
6. **Danach.** EMI zeigt die Bronze-Rezepte ohne Relog; die Ziel-Quest „Hearthstone“ auf K ist erledigt; der Hearthstone schwebt leuchtend über dem Sockel und lässt sich im Survival nicht abbauen; `/firmages shrine relics` listet ihn.
7. **Kaputt und repariert.** Einen Holzpfosten abbauen.
   Erwartung: Chat „The shrine is broken. Caelum's blessings rest until it is repaired.“, die Age bleibt. Pfosten wieder setzen: „The shrine stands again.“
8. **Zweites Herz.** Ein zweites Shrine Heart woanders setzen.
   Erwartung: wird verweigert („Caelum already dwells at ...“).

Hilfen: `/firmages shrine status` (Ringe mit fehlenden Blöcken, Opfer, Ritus, Gebet), `/firmages shrine locate`, `/firmages shrine extract <x y z>` (Relikt vom Sockel nehmen). Mit `debug.allowSimulate = true` in `firmages-server.toml` schließt `/firmages shrine simulate_pray` das Gebet sofort ab. Bronze-Ring (Ziegel, Bronzeblöcke, Glocke läuten) und Eisen-Ring (Pfeiler, Lampen anzünden) folgen demselben Ablauf mit Sky Disc und Steel Heart.

## Age-Übergang testen (Schrein, firmages-core 0.3.0)

Ziel: Du baust den Stone-Age-Schrein, legst den Hearthstone auf den Sockel, betest, und Caelum schaltet die Bronze Age frei. Stand 2026-10-01. Die Befehle von firmages-core 0.3.0 (`/firmages shrine status|locate|relics|extract|simulate_pray`, `/firmages ceremony preview`) stehen in `mod/firmages-core/SPEC.md` §10. Serverseitig ist der Ablauf auf dem Test-Server geprüft (`dev/poc-results.md`, „Shrine M4 and ceremony“); mit Client noch nie. Was abweicht, bitte notieren (Abschnitt „Was Du mir meldest“).

**Wo testen: auf dem Test-Server `test-server/`, nicht im Singleplayer.** Nur der dedizierte Server benutzt die Spiegeldatei `world/firmages/ages.json` und schickt den Reload wirklich übers Netz zum Client. Das ist der Fall, der beim Spielen mit Freunden zählt. Singleplayer startet immer im strikten Fallback (SPEC §3.2) und zeigt das Einfrieren anders.

### Voraussetzungen

- `mods/` im Repo enthält firmages-core **0.3.0** (oder neuer), und `tools\packwiz.exe refresh` ist gelaufen. Prüfen: http://localhost:8080/pack.toml öffnen, dann `index.toml` nach `firmages-core-0.3` durchsuchen.
- `packwiz serve` läuft (`dev\serve.bat`).
- Kein anderer Server läuft auf Port 25565.

### 1. Test-Server synchronisieren und starten

In einer Eingabeaufforderung (cmd), Fenster offen lassen:

```
cd /d D:\Minecraft\MinecraftModServer\FirmamentAges\test-server
..\tools\jdk-21\bin\java.exe -jar ..\tools\packwiz-installer-bootstrap.jar -g -s server http://localhost:8080/pack.toml
..\tools\jdk-21\bin\java.exe -Xms6G -Xmx8G @libraries/net/neoforged/neoforge/21.1.252/win_args.txt nogui
```

Die erste Zeile holt Mods, Configs und Skripte vom laufenden `packwiz serve`. Die zweite startet den Server mit Temurin 21. Nimm nicht `run.bat`: die nutzt das `java` aus dem PATH (Corretto 25). Fertig ist er bei `Done (…)`. In der Konsole muss stehen: `[firmages] on_stage_added: Age titles by firmages-core ceremony`. Steht dort `by KubeJS`, ist noch die alte Mod geladen.

Willst Du eine frische Welt: Server stoppen (`stop`), den Ordner `test-server\world` löschen und neu starten. Die TFC-Welt wird dann neu erzeugt (dauert einige Minuten).

### 2. Dich freischalten (in der Server-Konsole, ohne Schrägstrich)

```
whitelist add <DeinName>
op <DeinName>
```

Die Whitelist ist an (`white-list=true`, `enforce-whitelist=true`), ohne den ersten Befehl kommst Du nicht rein. Ohne OP gehen `/stage`, `/firmages` und `/gamemode` nicht.

### 3. Client starten und verbinden

Prism wie oben unter „Start“ (Schritte 3 bis 5), dann im Multiplayer `localhost` verbinden.

### 4. Schnellweg in die Stone Age (im Spiel)

```
/firmages ages
/stage grant @s age_0
/firmages ages
/gamemode creative
```

- Der erste `/firmages ages` zeigt `unlocked: [dawn]`.
- `/stage grant @s age_0` spielt die **kurze** Zeremonie (Titel, Klang, Strahl nur, wenn schon ein Schrein steht) und löst nach etwa 3 s einen Reload aus. Der Server friert dabei einige Sekunden ein, das ist gewollt.
- Der zweite `/firmages ages` zeigt `dawn, age_0`.
- Alternativ ohne Befehl: das Dawn-Kapitel spielen. The First Spark vergibt `age_0` selbst.

### 5. Shrine Heart besorgen

Survival-Weg (Ton und Grubenofen der Stone Age):

1. **Ton finden:** unter Gras nahe Wasser, erkennbar an den Ton-Pflanzen. Mit der Schaufel abbauen, das gibt Tonklumpen (`minecraft:clay_ball`, erst ab `age_0`).
2. **Formen:** mit 5 Ton in der Hand Benutzen drücken, das öffnet das Knapping-Raster. Das Muster des Unfired Hearth Idol zeigt EMI.
3. **Brennen im Grubenofen:** das ungebrannte Idol auf den Boden legen, 8 Stroh (`tfc:straw`) und dann 8 Stämme darauf, mit dem Feuerstarter (`tfc:firestarter`) anzünden. Der Ofen braucht feste Blöcke ringsum, am einfachsten in einer Grube 1 Block tief. Genau beschrieben im TFC Field Guide, Kapitel Pottery, Abschnitt Pit Kiln. Nach einigen Minuten liegt dort das gebrannte Hearth Idol.
4. **Herz bauen (Werkbank):** Holzkohle oben in der Mitte, darunter Bruchstein, Hearth Idol, Bruchstein, unten in der Mitte Bruchstein (Rezept `firmages:crafting/shrine_heart`). Für den Hearthstone brauchst Du ein zweites Idol.
5. **Sockel (Werkbank):** 4 Bruchstein, einer oben in der Mitte, drei darunter (`firmages:crafting/offering_plinth`).

Schnellweg:

```
/give @s firmages:shrine_heart
/give @s firmages:offering_plinth
```

### 6. Den Herdkreis (Ring 0) bauen

Platz suchen, mindestens 21 × 21 Blöcke flach (später kommen neun weitere Ringe dazu). Herz setzen. Dann:

```
/give @s tfc:rock/cobble/granite 16
/give @s tfc:wood/log/oak 16
/give @s tfc:thatch 8
```

Was der Ring braucht (Ring-Datei `shrine_ring_0.json` der Mod), 5 × 5 um das Herz, 22 Blöcke ohne das Herz:

| Block | Anzahl | Hinweis |
|---|---|---|
| Shrine Heart `firmages:shrine_heart` | 1 | Mitte |
| Bruchstein, beliebige TFC-Gesteinsart | 8 | direkt um das Herz, Tag `#firmages:shrine/hearth_stones` |
| Holzstamm (`#minecraft:logs`) | 8 | 4 Pfosten, je 2 hoch, an den Ecken des 5 × 5 |
| Stroh-Block `tfc:thatch` | 4 | je einer oben auf einem Pfosten |
| Opfersockel `firmages:offering_plinth` | 1 | Sockel 1, zwei Blöcke vor dem Herz in der Mitte einer Seite |

Die genaue Lage jedes Blocks legt die Ring-Datei der Mod fest. So siehst Du sie:

- Mit leerer Hand das Herz benutzen (ohne Schleichen; Schleichen ist Beten): Chat sagt, was fehlt, und eine Geistervorschau zeigt die fehlenden Blöcke an ihrem Platz.
- `/firmages shrine status` listet Ringe mit fehlenden Blöcken, Opfer, Ritus, Phase und Gebet.

Ist der Ring fertig, steigen Verzauberungs-Partikel über dem Herz auf (`ready=true`), und Benutzen sagt „Lay Hearthstone on its plinth first.“ Auf K wird jetzt „Raise the Shrine“ erfüllt, sobald Du das Herz anschaust („The Shrine Heart“ schon beim Setzen).

### 7. Opfern und beten

```
/give @s firmages:hearthstone
/give @s tfc:firestarter
/gamemode survival
```

(Survival-Weg zum Hearthstone: Hearth Idol aus 5 Ton formen und im Grubenofen brennen, dann Idol, 2 Kupferbarren und 2 Holzkohle in der Werkbank, Muster in EMI.)

1. **Opfern:** mit dem Hearthstone in der Hand den Sockel (oder das Herz) benutzen. Der Stein liegt danach schwebend über dem Sockel. Falsches Item: Absage mit dem Namen des erwarteten Items. Zurück bekommst Du ihn vor dem Beten mit Schleichen und Benutzen am Sockel.
2. **Ritus:** das Herz mit dem Feuerstarter (auch Feuerstein und Stahl, Feuerkugel oder Fackel) einmal benutzen, sofort, nicht gedrückt halten. Das Herz brennt danach (`lit=true`).
3. **Beten:** Hand leer, schleichen, Benutzen am Herz **gedrückt halten**, höchstens 6 Blöcke entfernt. Allein dauert es 10 s. Loslassen lässt den Fortschritt langsam sinken, nicht auf null.

### 8. Was Du sehen und hören solltest

Ungefähr in dieser Reihenfolge (SPEC §8, Zeiten ab Ende des Gebets):

| Zeit | Erwartung |
|---|---|
| 0 s | Hintergrundmusik verstummt, ein Klang, Partikel ziehen zum Herz |
| 2 s | Lichtstrahl in Bernstein steigt aus dem Herz, Chorklang |
| 3 s | Reload: der Server friert einige Sekunden ein, die Aktionsleiste zeigt „The world realigns...“; Effekte am Client laufen weiter |
| 4 s | Himmel und Nebel färben sich bis zum Ende (12 s) orange |
| 5 s | **Ein** Titel „The Bronze Age dawns“ mit Untertitel, eine Chatzeile „Caelum: A fire that keeps its shape. I see you.“ |
| 8 s | Chatzeile zum Segen „Hearthward“ |
| danach | Der Hearthstone bleibt sichtbar über dem Sockel. Kein zweiter Titel, kein Feuerwerk (das alte KubeJS-Feuerwerk ist ab 0.3.0 aus) |

Prüfen danach:

- `/firmages ages` zeigt `dawn, age_0, age_1`, dazu die Dauer des letzten Reloads.
- **EMI ohne Relog:** `create:andesite_alloy` und `create:mechanical_press` haben jetzt Rezepte. Vorher (in der Stone Age) waren sie unsichtbar. Gegenprobe: `tfc:metal/ingot/bronze` ist jetzt sichtbar.
- **K-Screen:** im Stone-Age-Kapitel ist „Offer the Hearthstone at the Shrine“ erledigt (wenn alle vier Keystones erledigt sind; vorher bleibt es offen, das ist so gewollt). Das Kapitel „Age 1: Bronze Age“ ist ohne Relog da. In „The Firmament“ ist Hearthstone abgehakt.
- `/stage list @s` zeigt `age_1` und `mob_1`.
- Gegenprobe kurze Zeremonie: `/firmages ceremony preview age_1 short` spielt sie nur Dir vor, ohne Vergabe.

### 9. Zurücksetzen und noch einmal

```
/stage revoke @s age_1
/firmages ages
/firmages shrine relics
/firmages shrine extract <x y z des Sockels>
/ftbquests change_progress @s reset 5298B856BFEE50A2
```

1. `/stage revoke` entzieht die Age in ProgressiveStages. Die Mod folgt, lädt neu und warnt „Age revoked: restart the server to clear in-progress items“.
2. `/firmages ages` muss wieder `dawn, age_0` zeigen.
3. `/firmages shrine relics` nennt die Position des Sockels, `/firmages shrine extract <x y z>` gibt den Hearthstone von dort zurück (Admin-Reparatur). Die Schrein-Daten merken sich nur das Relikt; die Age entzieht der Schrein nie.
4. Der letzte Befehl setzt das Ziel-Quest auf K zurück (Syntax aus dem FTB-Quests-Jar abgeleitet: `change_progress <Spieler> reset|complete <Quest-ID>`).
5. Danach den Server neu starten (`stop`, dann Schritt 1 ab der zweiten Zeile).

Nur wenn `/firmages ages` nach dem Revoke noch `age_1` zeigt (Mod und ProgressiveStages uneinig): Server stoppen, in `test-server\config\firmages-server.toml` unter `[debug]` `allowSimulate = true` setzen, starten, `/firmages ages simulate revoke age_1`, danach wieder `allowSimulate = false` und neu starten. `simulate` ändert nur den Zustand der Mod, nie die Stages; für den normalen Reset ist `/stage revoke` richtig.

Ganz frisch: Server stoppen, `test-server\world` löschen (siehe Schritt 1).

### Was Du mir meldest

- Jede Zeile der Tabelle in Schritt 8: gesehen ja/nein, ungefähre Zeit, Auffälliges.
- Wie lange der Server eingefroren war (`/firmages ages` nennt die Reload-Dauer) und wie lange EMI danach ruckelte. Grenze: unter 30 s, sonst fliegt der Client raus.
- Ob Du je Titel genau einen gesehen hast, bei `age_0` (kurz) und `age_1` (voll).
- Ob die Geistervorschau und `/firmages shrine status` zum gebauten Ring passten (sitzt die Vorschau genau um das Herz, nicht einen Block versetzt?).
- Ob es ein Rezept für das Shrine Heart und den Sockel gab (EMI) und wie es aussah.
- Ob „The Shrine Heart“, „Raise the Shrine“ und das Ziel-Quest auf K ohne Relog umsprangen.
- Fehlermeldungen im Chat und den Pfad zu `test-server\logs\latest.log`, falls etwas schiefging. Bei einem Client-Absturz: `tools\PrismLauncher\instances\FirmamentAgesDev\minecraft\crash-reports\`.

## Nach dem Test

- Ergebnisse direkt in `dev/poc-checklist.md` abhaken, Abweichungen daneben schreiben.
- Configs oder Skripte immer im Repo ändern, nie nur in der Instanz. Beim nächsten Start zieht der Pre-Launch-Sync den neuen Stand.
