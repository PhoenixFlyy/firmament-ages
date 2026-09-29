# CurseForge-only mods

**Stand 2026-09-30: alle 8 mit `packwiz curseforge add --addon-id <id> --file-id <id>` hinzugefuegt (mods/*.pw.toml, mode metadata:curseforge). CF-Abhaengigkeiten abgelehnt, weil sie als Modrinth-Eintraege schon im Pack sind.**

Diese Mods gibt es nicht auf Modrinth. Sie stehen noch nicht als `.pw.toml` im Repo, weil
packwiz fuer CurseForge-Dateien die CF-Datei-ID und einen Hash braucht (`packwiz curseforge add`
oder ein manuell geschriebenes `[update.curseforge]`), und die cfwidget-API liefert keinen Hash.
Quelle der Datei-Angaben: https://api.cfwidget.com/minecraft/mc-mods/<slug>, Stand beim Generieren.

| Mod | CF-Slug | CF-Projekt-ID | Datei fuer 1.21.1 NeoForge | Datei-ID | Kanal | Datum | Doc-Version | Grund |
|---|---|---|---|---|---|---|---|---|
| FTB Quests | `ftb-quests-forge` | 289412 | `ftb-quests-neoforge-2101.1.36.jar` | 8885017 | release | 2026-09-15 | 2101.1.36 | nur CurseForge |
| FTB Library | `ftb-library-forge` | 404465 | `ftb-library-neoforge-2101.1.37.jar` | 9008089 | release | 2026-09-29 | 2101.1.37 | nur CurseForge |
| FTB Teams | `ftb-teams-forge` | 404468 | `ftb-teams-neoforge-2101.1.11.jar` | 8724782 | release | 2026-08-24 | 2101.1.11 | nur CurseForge |
| FTB XMod Compat | `ftb-xmod-compat` | 889915 | `ftb-xmod-compat-neoforge-21.1.12.jar` | 8909889 | release | 2026-09-18 | 21.1.12 | nur CurseForge |
| FTB Chunks | `ftb-chunks-forge` | 314906 | `ftb-chunks-neoforge-2101.1.22.jar` | 8791113 | release | 2026-09-02 | 2101.1.22 | nur CurseForge |
| The Twilight Forest | `the-twilight-forest` | 227639 | `twilightforest-1.21.1-4.8.3345-universal.jar` | 7797302 | release | 2026-03-22 | 4.8.3345 | nur CurseForge |
| Torque Link Create To TFC | `torque-link-create-to-tfc` | 1481933 | `c2tfc-1.1.0-1.21.1.jar` | 8638215 | release | 2026-08-13 | 1.1.0 | nur CurseForge |
| AE2 Draconic Fusion Autocrafter | `ae2-draconic-fusion-autocrafter` | 1521464 | `ae2-draconic-fusion-autocrafter-neoforge-mc1.21.1-0.1.6.jar` | 8940388 | beta | 2026-09-21 | 0.1.6 | nur CurseForge |

Hinweise:

- FTB-Mods brauchen Architectury API; die liegt als Modrinth-Library schon in `mods/`.
- `.mrpack`-Export: CF-only-Jars duerfen nicht in die Modrinth-Datei; fuer den privaten Server
  reicht packwiz-installer mit `[update.curseforge]`-Eintraegen (Dok. 06).
- Eigene Mod `firmages-core` ist noch nicht gebaut und fehlt deshalb ebenfalls.
