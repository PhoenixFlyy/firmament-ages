# Firmament Ages auf CurseForge hochladen

Stand 2026-10-01, Pack 0.5.0. Reihenfolge: Konto, dann **zuerst firmages-core als Mod-Projekt**, dann das Modpack. Was vorher entschieden sein muss, steht in `dev/curseforge-readiness.md` (MekaTFC, TFC Ore Tooltips).

## 0. Vorher entscheiden

1. **MekaTFC und TFC Ore Tooltips** (`dev/curseforge-readiness.md`): Anträge stellen bzw. Ersatz wählen. Solange einer von beiden als Jar im Zip liegt und nicht auf der Approved-Liste steht, lehnt die Moderation das Modpack ab.
2. **Lizenz des Modpacks** (Feld beim Anlegen): Vorschlag **MIT** wie firmages-core; sie gilt nur für Deine eigenen Teile (Quests, KubeJS-Skripte, Configs), nicht für die Mods. „All Rights Reserved“ geht auch.

## 1. Autorenkonto

1. Auf [curseforge.com](https://www.curseforge.com) mit einem CurseForge-Konto anmelden (oder eins anlegen).
2. Die [Author Console](https://authors.curseforge.com) öffnen und die Autorenbedingungen annehmen. Dort legst Du Projekte an und lädst Dateien hoch.
3. Im Profil Name und Avatar setzen; der Autorname erscheint auf beiden Projekten. Das Pack führt Dich als **PhoenixFlyy** (`pack.toml`, `author`).

## 2. firmages-core als Mod-Projekt

Texte und Felder: `dev/cf/firmages-core-description.md` (Tabelle oben, Beschreibung zwischen den Linien, Checkliste unten).

1. **Jar:** `mods/firmages-core-0.6.1.jar` hochladen; es meldet `license="MIT"` in seiner `neoforge.mods.toml`, passend zur Projektlizenz.
2. Author Console → **Create Project** → Spiel *Minecraft*, Klasse **Mods**.
3. Felder: Name „Firmament Ages Core“, Summary aus der Tabelle, Beschreibung einfügen, Hauptkategorie *Miscellaneous*, weitere Kategorien laut Tabelle, Lizenz **MIT**, Avatar `dev/cf/avatar-400.png` (CurseForge verlangt 400 × 400).
4. Speichern, dann **Upload File**: das Jar, Release-Typ **Beta**, Spielversion **1.21.1**, Loader **NeoForge**, Java 21, Changelog = Abschnitte „0.6.1“ und „0.6.0“ der Beschreibung.
5. Beziehungen an der Datei: *Required Dependency* ProgressiveStages und Modonomicon, *Optional Dependency* KubeJS, Create, Immersive Engineering, Mekanism, Draconic Evolution, Stargate Journey, Jade.
6. Auf die Freigabe warten (meist Stunden, selten ein paar Tage). Danach auf der Dateiseite **Projekt-ID** und **Datei-ID** notieren.
7. Im Repo (Hauptordner):
   ```
   tools\packwiz.exe curseforge add --addon-id <Projekt-ID> --file-id <Datei-ID>
   del mods\firmages-core-0.6.1.jar
   tools\packwiz.exe refresh
   ```
   Abhängigkeitsfragen mit `n` beantworten (alle Abhängigkeiten sind schon im Pack). Dann `mods/*.pw.toml`, `index.toml` und `pack.toml` committen. Ab jetzt steht firmages-core im Manifest statt als Jar in `overrides/`.

## 3. Modpack-Projekt anlegen

1. Author Console → **Create Project** → *Minecraft*, Klasse **Modpacks**.
2. Felder:

| Feld | Wert |
|---|---|
| Name | Firmament Ages |
| Summary | A co-op TerraFirmaCraft progression pack: ten Ages from knapping flint to a Draconic reactor and a Stargate, one shrine, one final boss. |
| Beschreibung | Inhalt von `dev/cf/modpack-description.md` ab „# Firmament Ages“ (Markdown; im Editor auf *Markdown* umschalten oder als HTML einfügen). Vorher `python dev\cf\gen_credits.py`, damit die Credits zum aktuellen Stand passen. |
| Hauptkategorie | Tech |
| Weitere Kategorien | Quests, Magic, Multiplayer, Sci-Fi, Extra Large |
| Lizenz | Deine Wahl aus Schritt 0 (Vorschlag MIT) |
| Avatar | `dev/cf/avatar-400.png` (400 × 400); `dev/cf/logo.png` (512 × 512) nur, falls die Console es annimmt |
| Bilder (Gallery) | `dev/cf/banner.png` (1920 × 1080) und gern echte Screenshots aus dem Spiel |
| Quelle / Issues | leer lassen, solange das GitHub-Repo privat ist |

3. Speichern.

## 4. Modpack-Datei bauen und hochladen

1. Im Repo-Hauptordner:
   ```
   tools\packwiz.exe refresh
   python dev\cf\export_cf.py
   ```
   Das Skript schreibt `dev\exports\FirmamentAges-<Version>-curseforge-upload.zip` und listet die Manifest-Zahl und jedes Jar in `overrides/`. **Dort dürfen nur noch freigegebene Jars stehen** (heute: `firmages-core`, `mekatfc`, `TFC-Ore-Tooltips`; nach Schritt 0 und 2: keins).
   Nicht `dev\release.ps1` dafür nehmen: dessen CurseForge-Zip ist der private Prüfstand (Client-Seite, ohne Tausch der sechs gesperrten Mods).
2. Projekt → **Upload File**: das Zip.
3. Felder: Display Name „Firmament Ages 0.5.0“, Release-Typ **Beta**, Spielversion **1.21.1** (wird aus dem Manifest gelesen), Changelog = Inhalt von `dev/cf/changelog-0.5.0.md`.
4. Server-Pack: CurseForge will ihn als **Additional File** an genau dieser Datei, nicht als eigene Hauptdatei. Fürs erste weglassen; die Beschreibung kündigt ihn nur an.
5. Absenden.

## 5. Was die Moderation prüft

- **Erste Datei eines neuen Projekts:** Handprüfung, meist ein bis drei Tage. Spätere Dateien gehen oft schneller durch.
- **Fremde Jars in `overrides/`:** nur Mods der Approved-Liste und eigene Mods ohne CurseForge-Projekt. Ein Mod mit CurseForge-Projekt als Jar ist ein sicherer Ablehnungsgrund; deshalb tauscht `export_cf.py` die sechs gesperrten Mods gegen ihre CurseForge-Einträge.
- **Manifest:** CurseForge verlangt das Format der CurseForge App und verbietet Handänderungen am Manifest. packwiz schreibt genau dieses Format (`manifest.json`, `modlist.html`, `overrides/`), von Hand geändert wird nichts. Lehnt ein Moderator das Zip trotzdem ab („must be created with the CurseForge App“): das Zip in der CurseForge App importieren (*Create Custom Profile → Import*), Profil starten, einmal schließen, dann *Export Profile* mit den Ordnern `config`, `defaultconfigs`, `kubejs` und das exportierte Zip hochladen.
- **Beschreibung und Bilder:** Avatar nicht einfarbig, 400 × 400. Credits sind Pflicht für fremde Inhalte; der Credits-Abschnitt nennt jede Mod mit Autor und Link.
- **Loader:** alle Mods müssen NeoForge-Dateien für 1.21.1 sein. Das ist für alle 190 Manifest-Einträge so ausgewählt.
- **Gelöschte oder archivierte Dateien:** Zieht ein Mod-Autor eine Datei zurück, lehnt CurseForge spätere Uploads mit dieser Datei ab. Dann die Mod im Pack aktualisieren (`tools\packwiz.exe update <name>`) und neu exportieren.

## 6. Später: neue Version

1. Mod-Version geändert? Zuerst das neue firmages-core-Jar im Mod-Projekt hochladen und freigeben lassen, dann `tools\packwiz.exe curseforge add --addon-id <Projekt-ID> --file-id <neue Datei-ID>`.
2. `pack.toml` Version setzen (`dev\release.ps1 -Version x.y.z`), `python dev\cf\gen_credits.py`, `python dev\cf\export_cf.py`, Changelog schreiben (`dev/cf/changelog-x.y.z.md`), hochladen.
3. Neue Mods nur mit `tools\packwiz.exe curseforge add …`. Bricht danach der Installer der Freunde mit „must be downloaded manually“ ab, die Mod wie die sechs anderen behandeln: ihre CurseForge-Metadatei nach `dev/cf/swap/` verschieben, die Mod mit `tools\packwiz.exe modrinth add …` in derselben Datei-Version aufnehmen (gleicher Jar-Name) und den Namen in `DISTRIBUTION_BLOCKED` von `dev/cf_convert.py` nachtragen. `export_cf.py` tauscht sie dann von selbst.
