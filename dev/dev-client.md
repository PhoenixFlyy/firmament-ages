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
