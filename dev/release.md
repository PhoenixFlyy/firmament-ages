# Release: bauen, lokal testen, hochladen, verteilen

Eine Anleitung für jede Version.
Alle Befehle im Repo-Hauptordner `D:\Minecraft\MinecraftModServer\FirmamentAges`, Windows PowerShell 5.1 reicht.
`<v>` steht für die Pack-Version (z. B. `0.10.0`).

Reihenfolge einer Version: **1** bauen, **2** lokal testen, **3** firmages-core hochladen (wenn sich die Mod geändert
hat), **4** Modpack hochladen, **5** Freunde und Server.

## 1. Bauen

Questbuch zuerst: seit 1.0.1 sind die SNBT-Dateien die Quelle (Felix Q1A). Hat Felix im Spiel editiert, kommen seine
Dateien vor dem Bauen nach `config/ftbquests/quests/` (`dev/quests-editing.md`); dann
`python dev\validate_quests.py --style --mods <Mods-Ordner>` mit 0 Fehlern und 0 Warnungen. `dev\gen_quests.py`
gehört nicht mehr zum Bauen (läuft nur mit `--unfreeze` und würde seine Änderungen überschreiben).

```
powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -Version <v>
python dev\cf\gen_credits.py        # Credits aus mods/*.pw.toml in modpack-description.md
python dev\cf\md2html.py            # beide CurseForge-Beschreibungen als HTML
python dev\cf\export_cf.py          # das Upload-Zip
```

`release.ps1` prüft die Zeilenenden, führt `tools\packwiz.exe refresh` aus, setzt mit `-Version` die Version in
`pack.toml` **und** die Versionszeile des Titelbilds (`config/fancymenu/customization/title_screen_layout.txt`,
`source = Firmament Ages <v>`), dann noch ein `refresh`. Ohne `-Version` bricht es ab, wenn Titelbild und `pack.toml`
verschiedene Versionen nennen. Es schreibt nach `dev\exports\` (in `.gitignore`):

| Datei | Inhalt |
|---|---|
| `FirmamentAges-<v>.mrpack` | Modrinth-Export: Modrinth-Mods als Links, alle anderen Jars in `overrides/mods/` (privater Ersatzweg für Freunde ohne Prism) |
| `FirmamentAges-<v>-curseforge.zip` | `packwiz curseforge export -s client`, Prüfstand für `verify_install.py cfzip`, **nicht** die Upload-Datei |
| `FirmamentAges-<v>-server.zip` | das Server-Pack (startfertiger Server-Ordner, siehe Abschnitt 4), `-SkipServerPack` lässt es weg |
| `FirmamentAges-<v>.sha256.txt` | SHA-256 der Dateien |

Die Upload-Datei schreibt `export_cf.py`: `dev\exports\FirmamentAges-<v>-curseforge-upload.zip`. Es tauscht die neun
für Drittanbieter gesperrten Mods und das Shaderpack Complementary gegen ihre CurseForge-Einträge und listet am Ende
die Manifest-Zahl und jedes Jar in `overrides/`. **Dort darf nur noch `firmages-core-<Version>.jar` stehen**, nach
der Freigabe des Mod-Projekts (Abschnitt 3) gar keins.

Alle Exporte außer dem Upload-Zip enthalten fremde Jars: **nur privat** weitergeben, nie an ein öffentliches
GitHub-Release hängen. Das Skript committet, taggt und pusht nicht. Danach von Hand: `pack.toml`, `index.toml` und
das Titelbild-Layout committen, `dev` nach `main` mergen, Tag `vX.Y.Z`, push. Server und Freunde lesen `main`.

**Zeilenenden:** Liegt eine ausgelieferte Textdatei im Arbeitsordner mit CRLF vor, während Git LF speichert, steht in
`index.toml` ein anderer Hash als in der Datei, die GitHub ausliefert, und packwiz-installer bricht mit „Hash
invalid!“ ab. `release.ps1` stoppt dann und listet die Dateien; `-FixLineEndings` schreibt sie mit LF neu.

**Prüfen:**

```
python dev\verify_install.py mrpack dev\exports\FirmamentAges-<v>.mrpack --side client   # und --side server
python dev\verify_install.py cfzip  dev\exports\FirmamentAges-<v>-curseforge.zip
python dev\verify_install.py dir    <Installationsordner> --side client|server
```

Vergleicht Pfade und Hashes mit `index.toml`. Einen Installationsordner ohne Prism erzeugt
`tools\jdk-21\bin\java.exe -jar packwiz-installer-bootstrap.jar -g -s client|server <url>/pack.toml` in einem leeren
Ordner (lokal: `tools\packwiz.exe serve -p 8089`).

## 2. Lokal testen mit der CurseForge-App

Du testest genau das Upload-Zip. Die CurseForge-App lädt die Mods selbst von CurseForge, nur die eigenen Dateien
stecken im Zip.

### Importieren

Für jede Version das Profil **neu importieren**, ein altes Profil weder aktualisieren noch weiter nutzen: Default
Options (Shader-Voreinstellung, Sichtfeld, Tasten), das frühe Ladefenster und der Willkommensbildschirm greifen nur
beim ersten Start eines frischen Profils, und Client und Server brauchen dieselbe firmages-core-Version.

1. CurseForge-App starten, links **Minecraft**, oben **Create Custom Profile** (oder das Plus), dann **Import**.
2. Das Upload-Zip aus Abschnitt 1 wählen. Die App legt das Profil „Firmament Ages“ an und lädt alle 268 Mods und die
   zwei Shaderpacks (etwa 500 MB, einige Minuten). Java 21 richtet sie selbst ein; fragt sie nach einem Launcher, den
   normalen Minecraft Launcher nehmen.
3. Im Profil **Settings** den RAM prüfen: 8 GB. Das Upload-Zip trägt 8 GB als Empfehlung im Manifest (`recommendedRam`);
   steht dort noch der Standard von 4 GB, von Hand auf 8 GB setzen. Prüfpunkt: notieren, welchen Wert die App nach
   dem Import zeigt.
4. **Play**. Der erste Start braucht wegen der Shader-Vorbereitung drei bis fünf Minuten.

Bricht der Import mit einer Meldung zu einer Mod-Datei ab: Mod-Name und Meldung notieren, dann ist eine
CurseForge-Datei zurückgezogen worden (Abschnitt 4, „Gelöschte Dateien“).

### Mit dem Test-Server verbinden

Der Test-Server liegt in `test-server\` und läuft ohne Whitelist (`white-list=false` in
`test-server\server.properties`; steht dort noch `true`, nach dem Start einmal `whitelist off` tippen). Für eine
frische Dawn-Welt vorher `test-server\world` löschen. Dann in einem eigenen Fenster:

```
powershell -NoProfile -ExecutionPolicy Bypass -File dev\start_test_server.ps1
```

Der Server läuft im Vordergrund, bis Du `stop` und danach `stop confirm` eintippst
(`python dev\run_server.py` ist nur für automatische Läufe).
Nach `Done` in der CurseForge-App das Profil spielen, **Multiplayer**, Server `localhost`. Zuerst den Dawn-Join prüfen
(stiller Join, Willkommen, kein EMI und kein Jade), **erst danach** `op DEIN_NAME` in die Server-Konsole; sonst
stehen Op-Meldungen im Chat.

Von einem anderen PC im LAN: die IPv4-Adresse des Server-PCs eintragen (`ipconfig`). `server-ip=` in
`test-server\server.properties` muss leer sein, die Windows-Firewall Java im privaten Netz zulassen. RCON lauscht
dann im ganzen LAN auf Port 25575: Port 25575 nie im Router freigeben. Singleplayer im Profil geht auch; dann fehlt
nur der Mehrspieler-Teil der Zeremonie.

### Was Du testest und meldest

Die Checkliste steht in `dev/client-test-brief.md`, ein Abschnitt pro Version, neueste oben. Aus allen Versionen
gültig: Age-Übergang am Schrein (bauen, opfern, beten, Zeremonie, Weihe der Ringblöcke), Wartungsmodus (schleichend
mit leerer Hand auf das Herz schlagen), ein Siegel benutzen und die Wellen schlagen, EMI zeigt nach jeder neuen Age
deren Rezepte und vorher nicht, Dauer des Reload-Freezes.

Schnellweg in spätere Ages, als Operator: `/firmages ages`, `/stage grant @s age_4`. Zurück auf Dawn nur mit
`/firmages ages simulate revoke` und `debug.allowSimulate = true` in `test-server\config\firmages-server.toml`.

Melden: pro Punkt ja oder nein, bei Nein Chat-Meldung oder Screenshot; bei einem Absturz `test-server\logs\latest.log`
und den Crash-Report aus dem Profil (`Instances\Firmament Ages\crash-reports`); die Reload-Dauer aus `/firmages ages`.

## 3. firmages-core auf CurseForge (nur wenn sich die Mod geändert hat)

Felder: `dev/cf/firmages-core-fields.md`. Beschreibung: `dev/cf/firmages-core-description.html` in die HTML-Ansicht
des Editors. Die Mod geht **vor** dem Modpack hoch, weil das Modpack sie als Abhängigkeit nennt.

**Erstes Mal (Projekt anlegen):**

1. Auf [curseforge.com](https://www.curseforge.com) anmelden, die [Author Console](https://authors.curseforge.com)
   öffnen und die Autorenbedingungen annehmen; im Profil Name **PhoenixFlyy** (wie `author` in `pack.toml`) und
   Avatar setzen.
2. Das GitHub-Repo muss öffentlich sein (*Settings → General → Change visibility → Public*): beide Projekte nennen
   `https://github.com/PhoenixFlyy/firmament-ages` als Quelle und `…/issues` als Bugtracker. Im Repo liegen keine
   fremden Jars und keine Geheimnisse (`tools/`, `test-server/`, `dev/exports/` sind in `.gitignore`); `LICENSE` (Pack)
   und `mod/firmages-core/LICENSE` (Mod) sind MIT.
3. **Create Project** → *Minecraft*, Klasse **Mods**; Felder laut Tabelle, Avatar
   `dev/cf/firmages-core-avatar-400.png`, Lizenz **MIT**.

**Jede Version:**

1. **Upload File**: `mods/firmages-core-<Version>.jar`, Display Name „firmages-core <Version>“, Release-Typ **Beta**,
   Spielversion **1.21.1**, Loader **NeoForge**, Java 21, Changelog = die neuen Einträge aus
   `dev/cf/firmages-core-description.md`. Relationen: *Required Dependency* ProgressiveStages und Modonomicon,
   *Optional* laut Tabelle.
2. Auf die Freigabe warten (meist Stunden, selten Tage), dann Projekt-ID und Datei-ID notieren und im Repo:
   ```
   tools\packwiz.exe curseforge add --addon-id <Projekt-ID> --file-id <Datei-ID>
   del mods\firmages-core-<Version>.jar
   tools\packwiz.exe refresh
   ```
   Abhängigkeitsfragen mit `n` beantworten. `mods/firmages-core.pw.toml`, `index.toml`, `pack.toml` committen. Ab
   dann steht firmages-core im Manifest statt als Jar in `overrides/`; spätere Versionen mit
   `tools\packwiz.exe update firmages-core`.

## 4. Modpack auf CurseForge

Felder: `dev/cf/modpack-fields.md`. Beschreibung: `dev/cf/modpack-description.html` (vorher `gen_credits.py` und
`md2html.py`, Abschnitt 1).

**Erstes Mal (Projekt anlegen):** Author Console → **Create Project** → *Minecraft*, Klasse **Modpacks**; Name
„Firmament Ages“, Summary, Hauptkategorie *Tech*, weitere *Quests, Magic, Multiplayer, Sci-Fi, Extra Large*, Lizenz
**MIT**, Quelle und Issues (GitHub), Avatar `dev/cf/avatar-400.png`; Galerie `dev/cf/banner.png` und die Bilder aus
`dev/cf/gallery/` in der Reihenfolge von `modpack-fields.md`. Neu erzeugen, falls nötig: `python dev\cf\make_logo.py`,
`make_mod_logo.py`, `make_gallery.py` (braucht einen Lauf von `dev\shrine-viewer\render_shrine.py`).

**Jede Version:**

1. Abschnitt 2 durchgehen (das Upload-Zip in der App testen).
2. Projekt → **Upload File**: `dev\exports\FirmamentAges-<v>-curseforge-upload.zip`, Display Name „Firmament Ages
   <v>“, Release-Typ **Beta**, Spielversion **1.21.1**, Loader **NeoForge**, Changelog = der oberste Abschnitt von
   `CHANGELOG.md` (Editor auf Markdown stellen), Relation *Required Dependency* Firmament Ages Core.
3. Server-Pack als **Additional File** an genau dieser Datei (nicht als eigene Hauptdatei): Files → die eben
   hochgeladene Datei → **Upload Additional File** → `dev\exports\FirmamentAges-<v>-server.zip`, Display Name
   „Firmament Ages <v> Server Pack“, Release-Typ **Beta**, Changelog aus `modpack-fields.md`. Das Zip ist ein
   startfertiger Server-Ordner (`mods/`, `config/`, `defaultconfigs/`, `kubejs/`, Startskripte,
   `server.properties`, `eula.txt` mit `eula=false`, `README-SERVERPACK.md`, NeoForge-Installer), gefüllt von
   packwiz-installer (`-s server`) aus einem lokalen `packwiz serve`, genau wie ein echter Server synchronisiert. Größe
   etwa 510 MB.
4. Absenden.

**Was die Moderation prüft:**

- Erste Datei eines neuen Projekts: Handprüfung, meist ein bis drei Tage.
- Fremde Jars in `overrides/`: nur Mods der Approved-Liste und eigene Mods ohne CurseForge-Projekt. Ein Mod mit
  CurseForge-Projekt als Jar ist ein sicherer Ablehnungsgrund (deshalb der Tausch in `export_cf.py` und firmages-core
  als eigenes Projekt).
- Manifest: packwiz schreibt das Format der CurseForge App, von Hand wird nichts geändert. Lehnt ein Moderator das Zip
  trotzdem ab („must be created with the CurseForge App“): Zip in der App importieren, Profil starten und schließen,
  *Export Profile* mit `config`, `defaultconfigs`, `kubejs`, dieses Zip hochladen.
- Credits sind Pflicht: der Credits-Abschnitt der Beschreibung nennt jede Mod mit Autor und Link.
- Lizenz: Projekt MIT, das firmages-core-Jar meldet `license="MIT"`.
- Loader: alle Mods sind NeoForge-Dateien für 1.21.1; die zwei Shaderpacks haben keinen Loader.
- **Gelöschte Dateien:** Zieht ein Autor eine Datei zurück, lehnt CurseForge Uploads mit ihr ab. Dann die Mod
  aktualisieren (`tools\packwiz.exe update <name>`) und neu exportieren.

**Neue Mods** nur mit `tools\packwiz.exe curseforge add …`. Bricht danach der Installer der Freunde mit „must be
downloaded manually“ ab, die Mod wie die neun anderen behandeln: ihre CurseForge-Metadatei nach `dev/cf/swap/`
verschieben und in `mods/` eine Modrinth-Metadatei derselben Datei-Version anlegen (gleicher Jar-Name, gleiches
`side`), dann `refresh`; `export_cf.py` tauscht sie von selbst. Prüfen mit einer sauberen Client-Installation
(`dev/poc-results.md`, „Clean client install“).

**Mod-Updates von Enhanced Falling Trees und JourneyMap** vorher mit `javap` gegen die Ziele von firmages-core prüfen:
`firmages.mixins.json` ist `required`, ein fehlendes Mixin-Ziel bricht den Start ab, und die Reflection fällt sonst
still aus. Enhanced Falling Trees: `me.adda.enhanced_falling_trees.trees.DefaultTree` mit
`isMatchingLog(BlockState, BlockState)` und `isWithinDistance(BlockPos, BlockPos, BlockPos, int, int)` samt Konstante
32 (`mixin/eft/DefaultTreeMixin`). JourneyMap: `journeymap.api.client.impl.ClientAPI.INSTANCE` mit
`toggleMinimap(boolean)` und `minimapEnabled()`, die Tastennamen `key.journeymap.*` und die vier Kartenbildschirme
(`compat/journeymap/JourneyMapBridge`, `immersion/MapGate`). Passt etwas nicht, bleibt die Mod auf ihrer Version
(`pin`), bis firmages-core nachzieht.

**Nach jedem Mod-Update (Pflicht seit 1.0.2):**

```
python dev\check_mixin_refs.py --mods <Kopie>\mods --mods <Client-only-Jars>
```

Die Server-Jars einer synchronisierten Kopie plus die Client-only-Jars (`side = "client"`; z. B. ein Ordner mit
`packwiz-installer-bootstrap -g -s client` oder die CurseForge-Instanz des neuen Stands) müssen „MISSING: 0“ melden
(Exit 0). Das Werkzeug liest die Mixin-Klassen aller Jars samt Jar-in-Jar und meldet jede Klasse, die ein Mixin nennt
und kein Jar hat. So fiel 1.0.1 aus: WoodenCog 1.2.19 nennt in seinem JEI-Mixin `MixinBasinCategory`
`net.dries007.tfc.config.TemperatureDisplayStyle`, das TerraFirmaCraft 4.2.12 aufgeteilt hat; der Client stürzte beim
Laden ab, der Server-Boot zeigte nichts, weil der Mixin nur im Client läuft. Meldet es einen Fehler, bleibt die Mod
auf der alten Version (`pin = true` mit Kommentar), bis die abhängige Mod nachzieht. Meldet es einen optionalen Mixin
(Config-Plugin, `@Pseudo`, Soft-Dependency), den Grund am Jar mit `javap` belegen und in
`dev/data/mixin_refs_allow.json` eintragen. Einträge gelten je exaktem Jar-Namen: nach dem Update einer gelisteten
Mod kommen ihre Meldungen zurück, bis der Grund am neuen Jar neu belegt und der Eintrag umbenannt ist.

## 5. Freunde und Server

**Freunde (Prism, automatische Updates):**

```
powershell -NoProfile -ExecutionPolicy Bypass -File dev\make_friends_zip.ps1
```

Baut `dev\exports\FirmamentAges-Prism.zip`: `instance.cfg` (Pre-Launch-Befehl auf `main/pack.toml` bei GitHub, Java
automatisch, 4 bis 8 GB), `mmc-pack.json`, `minecraft/packwiz-installer-bootstrap.jar` und `README-FRIENDS.md` (Quelle
`dev\friends\README-FRIENDS.md`). Keine Mods, keine Configs, keine Account-Daten. Die Zip einmal schicken; jeder Start
holt danach den Stand von `main`. Ersatzweg ohne Prism: das `.mrpack` (Updates nur per neuem Import). Alle anderen
spielen über die CurseForge-App.

**Server:** `dev/README.md`, Abschnitt „How the server is set up“ (Vorlage aus `server/` kopieren, NeoForge-Installer daneben, `start.bat`; die Schleife synchronisiert
bei jedem Start von `main`). Updaten: nach `main` pushen, dann `restart` in der Server-Konsole (`stop`, dann `stop
confirm` beendet die Schleife). Vor jeder Änderung, die
Welten bricht, ein Backup.
