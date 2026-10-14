# Firmament Ages auf CurseForge hochladen

Stand 2026-10-14, Pack 0.8.1, firmages-core 0.8.1. Alle Entscheidungen sind getroffen (MekaTFC durch eine eigene Osmium-Ader ersetzt, TFC Ore Tooltips gestrichen, Lizenz MIT, Release-Typ Beta, GitHub-Repo öffentlich als Quelle). Reihenfolge: Konto, dann **zuerst firmages-core als Mod-Projekt**, dann das Modpack.

Jeder Feldwert steht fertig in zwei Dateien, hier wird nur verwiesen:

| Datei | Inhalt |
|---|---|
| `dev/cf/firmages-core-fields.md` | jedes Feld des Mod-Projekts (Name, Summary, Kategorien, Lizenz, Quelle, Datei, Relationen) |
| `dev/cf/modpack-fields.md` | jedes Feld des Modpack-Projekts (Name, Summary, Kategorien, Lizenz, Quelle, Galerie, Datei, Server-Pack) |
| `dev/cf/firmages-core-description.md` / `.html` | Beschreibung des Mod-Projekts (HTML für den Editor) mit Changelog 0.1.0 bis 0.8.1 |
| `dev/cf/modpack-description.md` / `.html` | Beschreibung des Modpacks mit Credits (207 Mods, Autor und Link) |
| `dev/cf/changelog-0.8.1.md` | Changelog der Modpack-Datei (kumuliert seit 0.5.0) |
| `dev/cf/avatar-400.png`, `logo.png`, `banner.png` | Modpack-Bilder (400², 512², 1920×1080) |
| `dev/cf/firmages-core-avatar-400.png`, `firmages-core-logo.png` | Mod-Bilder (400², 512²) |
| `dev/cf/gallery/01…05.png` | fünf Galeriebilder 1920×1080 (Schrein nach Stone, Arcane, Quantum Age, Bauplan, Signatur-Items) |
| `dev/cf/LOKAL-TESTEN.md` | das Upload-Zip vorher in der CurseForge-App testen |

Neu erzeugen, falls sich etwas geändert hat (Repo-Hauptordner):

```
python dev\cf\gen_credits.py        # Credits aus mods/*.pw.toml (MekaTFC und TFC Ore Tooltips sind ausgenommen)
python dev\cf\md2html.py            # beide Beschreibungen als HTML
python dev\cf\make_logo.py          # Modpack-Bilder aus dev\shrine-viewer\out\shrine_cutout_age_8.png
python dev\cf\make_mod_logo.py      # Mod-Bilder aus den Shrine-Heart-Texturen
python dev\cf\make_gallery.py       # Galerie (braucht einen Lauf von dev\shrine-viewer\render_shrine.py)
```

## 1. Autorenkonto

1. Auf [curseforge.com](https://www.curseforge.com) mit einem CurseForge-Konto anmelden (oder eins anlegen).
2. Die [Author Console](https://authors.curseforge.com) öffnen und die Autorenbedingungen annehmen. Dort legst Du Projekte an und lädst Dateien hoch.
3. Im Profil Name und Avatar setzen; der Autorname erscheint auf beiden Projekten. Das Pack führt Dich als **PhoenixFlyy** (`pack.toml`, `author`).

## 2. GitHub-Repo öffentlich

Beide Projekte nennen `https://github.com/PhoenixFlyy/firmament-ages` als Quelle und `…/issues` als Bugtracker. Vorher auf GitHub: *Settings → General → Danger Zone → Change visibility → Public*. Im Repo liegen keine fremden Jars und keine Geheimnisse (`tools/`, `test-server/`, `dev/exports/` sind in `.gitignore`); `mod/firmages-core/LICENSE` ist MIT, eine `LICENSE` im Hauptordner für die eigenen Pack-Teile (Quests, Skripte, Configs, Texturen) fehlt noch, siehe offene Punkte in `dev/decisions-while-away.md`.

## 3. firmages-core als Mod-Projekt

Felder: `dev/cf/firmages-core-fields.md`. Beschreibung: `dev/cf/firmages-core-description.html` in die HTML-Ansicht des Editors.

1. Author Console → **Create Project** → Spiel *Minecraft*, Klasse **Mods**; Felder laut Tabelle, Avatar `dev/cf/firmages-core-avatar-400.png`, Lizenz **MIT**, Quelle und Issues laut Tabelle.
2. Speichern, dann **Upload File**: `mods/firmages-core-0.8.1.jar`, Display Name „firmages-core 0.8.1“, Release-Typ **Beta**, Spielversion **1.21.1**, Loader **NeoForge**, Java 21, Changelog = Einträge 0.8.1, 0.8.0, 0.7.2, 0.7.1, 0.7.0 und 0.6.2 aus der Beschreibung.
3. Relationen an der Datei: *Required Dependency* ProgressiveStages und Modonomicon, *Optional Dependency* laut Tabelle.
4. Auf die Freigabe warten (meist Stunden, selten ein paar Tage). Danach auf der Dateiseite **Projekt-ID** und **Datei-ID** notieren.
5. Im Repo (Hauptordner):
   ```
   tools\packwiz.exe curseforge add --addon-id <Projekt-ID> --file-id <Datei-ID>
   del mods\firmages-core-0.8.1.jar
   tools\packwiz.exe refresh
   ```
   Abhängigkeitsfragen mit `n` beantworten (alle Abhängigkeiten sind schon im Pack). Dann `mods/firmages-core.pw.toml`, `index.toml` und `pack.toml` committen. Ab jetzt steht firmages-core im Manifest statt als Jar in `overrides/`.

## 4. Modpack-Projekt anlegen

Felder: `dev/cf/modpack-fields.md`. Beschreibung: `dev/cf/modpack-description.html` in die HTML-Ansicht des Editors (vorher `python dev\cf\gen_credits.py`, falls sich `mods/` geändert hat).

1. Author Console → **Create Project** → *Minecraft*, Klasse **Modpacks**; Name „Firmament Ages“, Summary, Hauptkategorie *Tech*, weitere Kategorien *Quests, Magic, Multiplayer, Sci-Fi, Extra Large*, Lizenz **MIT**, Quelle und Issues (GitHub), Avatar `dev/cf/avatar-400.png`.
2. Galerie: `dev/cf/banner.png` und die fünf Bilder aus `dev/cf/gallery/` in der Reihenfolge der Tabelle in `modpack-fields.md`; echte Screenshots aus dem Spiel später dazu.
3. Speichern.

## 5. Modpack-Datei bauen und hochladen

1. Vorher einmal `dev/cf/LOKAL-TESTEN.md` durchgehen (das Zip in der CurseForge-App importieren und starten).
2. Im Repo-Hauptordner:
   ```
   tools\packwiz.exe refresh
   python dev\cf\export_cf.py
   ```
   Das Skript schreibt `dev\exports\FirmamentAges-0.8.1-curseforge-upload.zip` und listet die Manifest-Zahl (für 0.8.1: 208 Einträge, 206 Mods und 2 Shaderpacks; „swapped 9“) und jedes Jar in `overrides/`. **Dort darf nur noch `firmages-core-0.8.1.jar` stehen** (nach Schritt 3.5: keins). MekaTFC und TFC Ore Tooltips sind nicht mehr im Pack.
   Nicht `dev\release.ps1` dafür nehmen: dessen CurseForge-Zip ist der private Prüfstand (Client-Seite, ohne Tausch der acht gesperrten Mods und von Complementary).
3. Projekt → **Upload File**: das Zip, Display Name „Firmament Ages 0.8.1“, Release-Typ **Beta**, Spielversion **1.21.1** (wird aus dem Manifest gelesen), Loader **NeoForge**, Changelog = Inhalt von `dev/cf/changelog-0.8.1.md`, Relation *Required Dependency* Firmament Ages Core (sobald das Mod-Projekt existiert).
4. Server-Pack als **Additional File** an genau dieser Datei (nicht als eigene Hauptdatei):
   ```
   powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1
   ```
   Schritt 5 des Skripts schreibt `dev\exports\FirmamentAges-0.8.1-server.zip`: ein startfertiger Server-Ordner (`mods/` mit allen Server-Jars, `config/`, `defaultconfigs/`, `kubejs/`, `start.bat`, `start.ps1`, `user_jvm_args.txt`, `server.properties`, `README-SERVERPACK.md`, NeoForge-Installer), gefüllt von packwiz-installer (`-s server`) aus einem lokalen `packwiz serve`, also genau so wie ein echter Server synchronisiert. `packwiz curseforge export -s server` schreibt nur ein Manifest-Zip; das installiert CurseForge für Server nicht.
   Auf der Dateiseite des Modpacks (Files → die eben hochgeladene Datei) **Upload Additional File** wählen, das Server-Zip hochladen, Display Name „Firmament Ages 0.8.1 Server Pack“, Release-Typ **Beta**, Changelog aus `modpack-fields.md`. Größe etwa 510 MB; eine Grenze nennt CurseForge nicht, lehnt die Console die Größe ab, Support fragen.
5. Absenden.

## 6. Was die Moderation prüft

- **Erste Datei eines neuen Projekts:** Handprüfung, meist ein bis drei Tage. Spätere Dateien gehen oft schneller durch.
- **Fremde Jars in `overrides/`:** nur Mods der Approved-Liste und eigene Mods ohne CurseForge-Projekt. Ein Mod mit CurseForge-Projekt als Jar ist ein sicherer Ablehnungsgrund; deshalb tauscht `export_cf.py` die acht gesperrten Mods und das Shaderpack Complementary gegen ihre CurseForge-Einträge, und deshalb kommt firmages-core zuerst als eigenes Projekt.
- **Manifest:** CurseForge verlangt das Format der CurseForge App und verbietet Handänderungen am Manifest. packwiz schreibt genau dieses Format (`manifest.json`, `modlist.html`, `overrides/`), von Hand geändert wird nichts. Lehnt ein Moderator das Zip trotzdem ab („must be created with the CurseForge App“): das Zip in der CurseForge App importieren (*Create Custom Profile → Import*), Profil starten, einmal schließen, dann *Export Profile* mit den Ordnern `config`, `defaultconfigs`, `kubejs` und das exportierte Zip hochladen (`LOKAL-TESTEN.md`).
- **Beschreibung und Bilder:** Avatar nicht einfarbig, 400 × 400. Credits sind Pflicht für fremde Inhalte; der Credits-Abschnitt nennt jede der 207 Mods mit Autor und Link.
- **Lizenz:** Projektlizenz MIT; das Jar von firmages-core meldet `license="MIT"` in seiner `neoforge.mods.toml`, Moderatoren vergleichen beides. Der Lizenztext liegt im Repo als `LICENSE` (eigene Teile des Packs) und `mod/firmages-core/LICENSE` (die Mod).
- **Loader:** alle Mods müssen NeoForge-Dateien für 1.21.1 sein. Das ist für alle 206 Mods im Manifest so ausgewählt; die zwei Shaderpacks (Complementary Reimagined, MakeUp Ultra Fast) sind keine Mods und haben keinen Loader.
- **Gelöschte oder archivierte Dateien:** Zieht ein Mod-Autor eine Datei zurück, lehnt CurseForge spätere Uploads mit dieser Datei ab. Dann die Mod im Pack aktualisieren (`tools\packwiz.exe update <name>`) und neu exportieren.

## 7. Später: neue Version

1. Mod-Version geändert? Zuerst das neue firmages-core-Jar im Mod-Projekt hochladen und freigeben lassen, dann `tools\packwiz.exe update firmages-core` (oder `curseforge add` mit der neuen Datei-ID). Changelog-Eintrag in `dev/cf/firmages-core-description.md` ergänzen.
2. `pack.toml` Version setzen (`dev\release.ps1 -Version x.y.z`), `python dev\cf\gen_credits.py`, `python dev\cf\md2html.py`, `python dev\cf\export_cf.py`, Changelog schreiben (`dev/cf/changelog-x.y.z.md`), hochladen; Display Name und Server-Pack-Name mit der neuen Version.
3. Neue Mods nur mit `tools\packwiz.exe curseforge add …`. Bricht danach der Installer der Freunde mit „must be downloaded manually“ ab, die Mod wie die acht anderen behandeln: ihre CurseForge-Metadatei nach `dev/cf/swap/` verschieben und in `mods/` eine Modrinth-Metadatei derselben Datei-Version anlegen (gleicher Jar-Name, gleiches `side`; mit `tools\packwiz.exe modrinth add …` oder von Hand wie `mods/more-darkness.pw.toml`), dann `tools\packwiz.exe refresh`. `export_cf.py` tauscht sie dann von selbst. `DISTRIBUTION_BLOCKED` in `dev/cf_convert.py` gehört nur zur einmaligen Umstellung vom 2026-10-01 und bleibt unverändert. Prüfen mit einer sauberen Client-Installation (`dev/poc-results.md`, „Clean client install“).
