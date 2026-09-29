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
2. **Test-Server starten** (siehe `server/README.md`): `test-server\start.bat`. In der Server-Konsole Dich zum OP machen: `op <DeinName>`. Ohne OP gehen `/stage` und `/fa_reconcile` nicht.
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

1. Einen zweiten Account (oder Freund) online haben, wenn möglich.
2. `/stage grant @s age_0`, dann `/stage grant @s age_1`.
   Erwartung bei allen Online-Spielern: Titel „Age 1: Bronze Age“, Untertitel, Sound, Feuerwerk, Chatzeile aus `unlock_message` („the first alloys. New veins can be found.“).
3. `/stage grant @s age_2`.
   Erwartung: Titel „Age 2: Iron Age“, Chatzeile „the map opens and iron veins become visible.“
4. Beim Entziehen (`/stage revoke @s age_2`) darf kein Titel kommen. Wenn doch, notieren.

## Nach dem Test

- Ergebnisse direkt in `dev/poc-checklist.md` abhaken, Abweichungen daneben schreiben.
- Configs oder Skripte immer im Repo ändern, nie nur in der Instanz. Beim nächsten Start zieht der Pre-Launch-Sync den neuen Stand.
