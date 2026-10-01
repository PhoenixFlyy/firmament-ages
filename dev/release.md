# Release und Verteilung

Stand 2026-10-01. Alle Befehle im Repo-Wurzelordner, Windows PowerShell 5.1 reicht.

## Release bauen

```
powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1                  # ohne Versionswechsel
powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -Version 0.5.0   # setzt version in pack.toml
```

Das Skript prüft die Zeilenenden, führt `tools\packwiz.exe refresh` aus, setzt optional die Version in `pack.toml` (danach noch ein `refresh`) und schreibt nach `dev\exports\` (in `.gitignore`):

| Datei | Befehl | Inhalt |
|---|---|---|
| `FirmamentAges-<v>.mrpack` | `packwiz modrinth export -o …` | 184 Modrinth-Mods als Download-Links, die 8 CurseForge-only-Mods und `firmages-core-*.jar` als Jars in `overrides/mods/`, dazu `config/`, `defaultconfigs/`, `kubejs/` |
| `FirmamentAges-<v>-curseforge.zip` | `packwiz curseforge export -s client -o …` | die 8 CurseForge-Mods im Manifest, alle anderen Jars (Modrinth, firmages-core) eingebettet |
| `FirmamentAges-<v>.sha256.txt` | `Get-FileHash` | SHA-256 beider Dateien |

Beide Exporte laufen ohne Zusatzschalter durch (`--restrictDomains` bleibt an; alle Modrinth-Dateien liegen auf `cdn.modrinth.com`). Weil beide Dateien fremde Jars enthalten, **nur privat** weitergeben, nie an ein öffentliches GitHub-Release hängen.

Das Skript committet, taggt und pusht nicht. Danach von Hand: `pack.toml`/`index.toml` committen, `dev` nach `main` mergen, Tag `vX.Y.Z`, push. Server und Freunde lesen `main`.

**Zeilenenden:** Liegt eine ausgelieferte Textdatei im Arbeitsordner mit CRLF vor, während Git LF speichert, steht in `index.toml` ein anderer Hash als in der Datei, die GitHub ausliefert. packwiz-installer bricht dann mit „Hash invalid!“ ab. `release.ps1` stoppt in diesem Fall und listet die Dateien; `-FixLineEndings` schreibt sie mit LF neu (Git-Inhalt bleibt gleich).

## Prüfen

```
python dev\verify_install.py mrpack dev\exports\FirmamentAges-<v>.mrpack --side client   # und --side server
python dev\verify_install.py cfzip  dev\exports\FirmamentAges-<v>-curseforge.zip
python dev\verify_install.py dir    <Installationsordner> --side client|server
```

Vergleicht Pfade und Hashes mit `index.toml` (Seite `both` plus `client` bzw. `server`). Einen Installationsordner erzeugt man ohne Prism mit `tools\jdk-21\bin\java.exe -jar packwiz-installer-bootstrap.jar -g -s client|server <url>/pack.toml` im leeren Ordner (lokal: `tools\packwiz.exe serve -p 8089`).

## Freunde

```
powershell -NoProfile -ExecutionPolicy Bypass -File dev\make_friends_zip.ps1
```

Baut `dev\exports\FirmamentAges-Prism.zip` aus der Dev-Instanz: `instance.cfg` (Name „Firmament Ages“, Pre-Launch-Befehl auf `main/pack.toml` bei GitHub, Java automatisch, 4 bis 8 GB), `mmc-pack.json`, `minecraft/packwiz-installer-bootstrap.jar` und `README-FRIENDS.md` (6 Schritte, Quelle `dev\friends\README-FRIENDS.md`). Keine Mods, keine Configs, keine Account-Daten. Die Zip schickst Du einmal; jeder Start holt danach den aktuellen Stand (Weg A aus Dok. 06 §5.2). Das `.mrpack` ist der Ersatzweg für jemanden ohne Prism (Import in Prism oder Modrinth App, Updates nur per neuem Import).

## Server

Siehe `server/README.md`: Vorlage kopieren, NeoForge-Installer daneben, `start.bat`.
