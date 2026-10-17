# Quests im Spiel bearbeiten (ab Pack 1.0.1)

Felix hat am 2026-10-17 entschieden (Q1A): ab 1.0.1 bearbeitest Du das Questbuch („the Chronicle“) selbst im Spiel,
im Edit-Modus von FTB Quests. Ab da sind die SNBT-Dateien in `config/ftbquests/quests/` die Quelle des Buchs.
`dev/gen_quests.py`, das Skript, das das Buch bis 1.0.0 geschrieben hat, läuft nur noch mit `--unfreeze`, weil es
sonst Deine Änderungen überschreiben würde. Claude führt es nicht mehr aus, solange Du nichts anderes sagst.

## 1. Edit-Modus einschalten

1. Du brauchst Operator-Rechte: auf dem Server in der Konsole `op <Dein Name>`, im Einzelspieler eine Welt mit
   Cheats (oder „Im LAN öffnen“ mit Cheats an).
2. Im Spiel `/ftbquests editing_mode` eingeben (schaltet um; `/ftbquests editing_mode true` bzw. `false` setzt ihn
   fest). Mit **K** das Buch öffnen: im Edit-Modus kannst Du Quests mit Rechtsklick bearbeiten, verschieben, neu
   anlegen und löschen, und Texte direkt ändern.
3. Fertig? Edit-Modus wieder aus (`/ftbquests editing_mode false`), damit Du das Buch wie ein Spieler siehst.

Tipps für das Editieren:

- **Texte** (Titel, Untertitel, Beschreibung) speichert FTB Quests in `lang/en_us.snbt`, nicht im Kapitel. Spielertext
  bleibt Englisch und folgt `dev/style-guide.md` (kurz, ohne „!“, keine Erklärtexte zu Features).
- **Art einer Quest** steht in ihren Tags (Quest bearbeiten, Feld „Tags“): `entry`, `keystone`, `goal`, `side`,
  `explain`, `ring`, `guide`, `info`, `note`. Eine Quest ohne Tag ist ein normaler Schritt. Eine Quest, die nur zum
  Lesen da ist (Häkchen), braucht das Tag `note` oder `explain`, sonst verlangt die Prüfung eine Item-Belohnung.
- **Belohnungen:** Items, nicht XP (Abschnitt 4).
- Quests nicht per Hand zwischen Dateien kopieren; FTB Quests vergibt die IDs selbst. Der Fortschritt der Teams liegt
  in der Welt und hängt an diesen IDs.

## 2. Wo der Server die Dateien ablegt

FTB Quests schreibt jede Änderung sofort in den Ordner des Spiels, in dem Du editierst:

- Server: `<Server-Ordner>/config/ftbquests/quests/`
- Einzelspieler (CurseForge-Profil): `<Instanz>/config/ftbquests/quests/`, z. B.
  `C:\Users\Felix\curseforge\minecraft\Instances\Firmament Ages\config\ftbquests\quests\`

Darin: `data.snbt`, `chapter_groups.snbt`, `chapters/*.snbt` (ein Kapitel je Datei), `lang/en_us.snbt` (alle Texte).

**Wichtig:** Das Pack-Update (packwiz) schreibt diese Dateien auf dem Server neu, sobald sich die Version im Repo
ändert. Kopiere Deine Änderungen deshalb ins Repo (Abschnitt 3), **bevor** das nächste Pack-Update den Server erreicht,
sonst sind sie weg.

## 3. Änderungen ins Repo bringen

1. Den ganzen Ordner `config/ftbquests/quests/` vom Server bzw. aus der Instanz nach
   `D:\Minecraft\MinecraftModServer\FirmamentAges\config\ftbquests\quests\` kopieren und vorhandene Dateien ersetzen.
   Gelöschte Kapitel auch im Repo löschen (am einfachsten: Repo-Ordner `chapters/` vorher leeren).
2. Claude Bescheid sagen. Claude
   - prüft das Buch (Abschnitt 4) und zeigt Dir, was die Prüfung anmahnt,
   - führt `tools\packwiz.exe refresh` aus (ohne das passt `index.toml` nicht zu den neuen Dateien und packwiz-installer
     bricht mit „Hash invalid!“ ab),
   - achtet auf LF-Zeilenenden und committet.

## 4. Was die Prüfung anmahnt

`python dev/validate_quests.py --style --mods <Mods-Ordner>` liest nur die SNBT-Dateien (kein Generator nötig).
Ziel: 0 Fehler, 0 Warnungen. Die wichtigsten Regeln:

| Regel | Was passiert |
|---|---|
| Items statt XP | Jede Quest, die Arbeit verlangt, gibt Items ihrer Age. Nur Lese-Quests (Tag `entry`, `explain`, `info`, `note`, `guide`) geben XP und keine Items. Fehler „no item reward“ bzw. „pays XP only, no items“. |
| Keine XP neben Items | Eine Quest mit Item-Belohnung gibt keine XP dazu. Fehler „xp beside item rewards“. |
| Belohnungen sind nicht Aufgaben-Items | Für jede Item-Aufgabe dürfen die Belohnungen anderer Quests derselben oder einer früheren Age höchstens ein Drittel der verlangten Menge geben; ein Aufgaben-Item einer Side Road zahlt keine fremde Quest; nie Age-Token, Opfergabe oder Chaos-Splitter; in Dawn keine Fackel. |
| Age-Erreichbarkeit | Belohnungs- und Aufgaben-Items gehören zur Age des Kapitels oder früher (`age_items`-Tags). Q-1 (`python dev/derive_ages.py --dump-dir <Dump>`, braucht einen Boot): jedes verlangte Item ist bis zur Age des Kapitels über Rezept, Welt oder Beute erreichbar, nicht nur als Belohnung; Q-2: Items, die nur aus Belohnungen kommen, stehen in `REWARD_ONLY`. |
| Struktur | Jedes Age-Kapitel hat einen Eingang, ein Ziel, 3 bis 5 Keystones, 2 bis 6 optionale Nebenquests; Abhängigkeiten zeigen auf vorhandene Quests. |
| Text | Jede Quest hat einen englischen Titel, keine deutschen Umlaute im Spielertext; mit `--style` Wortgrenzen und Stil aus `dev/style-guide.md` (Warnungen). |
| Karte | Quests nicht zu dicht (`dev/quest_layout.py` MIN_GAP), mit `--style` höchstens 2 Linienkreuzungen je Kapitel. |

Die Vorschau aller Kapitel zeichnet `python dev/preview_quests.py` nach `dev/quest-preview/`.

## 5. Der Generator

`python dev/gen_quests.py` bricht ohne Flag mit einem Hinweis ab. `python dev/gen_quests.py --unfreeze` schreibt das
ganze Buch aus dem Skript neu: nur, wenn Deine Änderungen vorher ins Skript übernommen wurden oder Du ausdrücklich
zustimmst. Stand beim Einfrieren (1.0.1): Generator und SNBT-Dateien sind gleich (26 Kapitel, 667 Quests).
