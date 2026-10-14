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
2. **Test-Server starten:** `dev/release.md`, Abschnitt 2 („Mit dem Test-Server verbinden“). Ohne OP gehen `/stage` und `/fa_reconcile` nicht.
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

Jeder Befehl wurde aus Doku, Quellcode oder `server/README.md` abgeleitet. Im Spiel gelaufen ist noch keiner. Weicht die Syntax ab, hier korrigieren.

## Titelbild neu erzeugen (Constellation)

Der Titelbildschirm ist ein GLSL-Shader als FancyMenu-Hintergrund. Quelle, Generator und Anleitung stehen in `dev/title-shader/README.md`.

## Boot- und Heap-Budget der Deko-Mods messen (n2-deco-mods P5)

Die Deko-Runde (Branch `n3/deco`: Moonlight, Supplementaries, FirmaSupplementaries, Every Compat, Handcrafted, Decorative Blocks Reborn, Macaw's Windows, Bridges und Doors, rund 2.900 neue Items) ist nur auf dem Client messbar. Every Compat baut Modelle und Texturen für 34 TFC/AFC-Hölzer beim Start (Moonlight Dynamic Pack). Budget gegenüber dem Stand ohne die Runde: **höchstens +20 s bis zum Titelbildschirm und höchstens +300 MB Heap nach dem Weltbeitritt.**

1. **Zwei Stände ausliefern.** Basis = `dev` vor dem Merge von `n3/deco` (Commit `c7ae645`), Kandidat = `dev` nach dem Merge. Für jeden Stand `tools\packwiz.exe serve` im jeweiligen Checkout (nacheinander, beide auf Port 8080), dann die Instanz *Firmament Ages Dev* starten; der Pre-Launch-Sync holt genau diesen Stand.
2. **Gleiche Bedingungen.** Derselbe Rechner, Prism mit 10 GB, keine anderen Spiele oder Builds nebenher. Nach jedem Standwechsel einen **Aufwärmstart** verwerfen (Every Compat und Moonlight bauen beim ersten Start Caches, Shader werden kompiliert).
3. **Bootzeit, drei Läufe je Stand.** Instanz starten, bis zum Titelbildschirm warten, Spiel schließen. Die Zeit steht in `tools\PrismLauncher\instances\FirmamentAgesDev\minecraft\logs\latest.log` in der ModernFix-Zeile „Game took … seconds to start“. Mittelwert der drei Läufe notieren.
4. **Heap, drei Läufe je Stand.** Mit dem Test-Server verbinden (oder dieselbe Einzelspielerwelt laden), an der Spawn-Stelle 60 s stillstehen, dann `/sparkc health --memory` im Chat. Den Wert „used“ des Heaps notieren (spark zeigt ihn im Chat, ohne Upload). Mittelwert der drei Läufe.
5. **Every-Compat-Zählung.** Im Kandidaten unter *Mods → Every Compat → Config* zeigt das Overlay die Zahl der registrierten Blöcke. Erwartet: etwa 1.630 (Handcrafted 510, Decorative Blocks 136, Windows 442, Bridges 204, Doors 272, Create-Fenster 68), keine Twilight-Forest-, Ad-Astra-, Ars-, Cataclysm-, Occultism- oder Totemic-Hölzer.
6. **Zwei optionale Hebel, je einmal messen** (nur behalten, wenn sie messbar helfen und im Log nichts fehlt): `config/everycomp-client.toml` mit `[general]` `dynamic_assets_generation_mode = "CACHED"` (schnellerer zweiter Start?) und `config/everycomp-common.toml` mit `[general]` `generate_blocktype_tags = false` (danach im Log nach fehlenden Rezeptzutaten von Every Compat oder Supplementaries suchen).
7. **Bewerten.** Kandidat minus Basis. Liegt einer der beiden Werte über dem Budget, gilt diese Reihenfolge, nach jedem Schritt neu messen:
   1. Macaw's Doors entfernen (`mods/macaws-doors.pw.toml` löschen, `tools\packwiz.exe refresh`, die `mcwdoors`-Zeilen in `config/everycomp-hazardous.toml` und `dev/age_map.toml` entfernen).
   2. Macaw's Windows weiter ausdünnen: in `config/everycomp-hazardous.toml` `[entryset]` zusätzlich `mcwwindows:window2`, `mcwwindows:stripped_log_window2`, `mcwwindows:plank_window2`, `mcwwindows:log_parapet`, `mcwwindows:plank_parapet` sperren.
   3. Handcrafted kürzen: `handcrafted:pillar_trim`, `handcrafted:corner_trim`, `handcrafted:counter` in denselben `[entryset]`-Block.
8. **Ergebnis** als Tabelle (Stand, Lauf, Bootzeit, Heap) in `dev/poc-results.md` eintragen und die Entscheidung als Zeile in `dev/decisions-while-away.md`.
