# Das Questbuch pflegen (ab Runde G4, 2026-10-18)

Felix hat am 2026-10-18 die Questbearbeitung an Claude zurückgegeben. Seitdem ist `dev/gen_quests.py` wieder die
Quelle des Buchs („the Chronicle“): Claude pflegt das Buch, Felix gibt Wünsche. Der Freeze von 1.0.1 (Felix Q1A:
SNBT-Dateien als Quelle, Bearbeitung im Spiel) ist aufgehoben; Felix' In-Game-Änderungen von 1.2.0 („By Hand“ in
„Hands and Keys“ aufgegangen, „The First Spark“ wartet auf „You Wake“, der Absatz über die Bauanleitungs-Diamanten
ist weg) stehen im Generator.

## 1. So läuft es

1. Felix nennt den Wunsch (Text, Reihenfolge, Icon, Abhängigkeit, Belohnung, eine Quest weg oder dazu), gern mit dem
   Questtitel oder einem Screenshot.
2. Claude ändert `dev/gen_quests.py` (Kapitel `DAWN` bis `SINGULARITY`, `FIRMAMENT`, `ROADS`, `WORKS`), führt
   `python dev/gen_quests.py` aus und prüft mit `python dev/validate_quests.py --style --mods <Mods-Ordner>` (0 Fehler,
   0 Warnungen) und `python dev/preview_quests.py` (Kapitelbilder in `dev/quest-preview/`).
3. Danach `tools\packwiz.exe refresh` (sonst passt `index.toml` nicht zu den Dateien und packwiz-installer bricht mit
   „Hash invalid!“ ab), LF-Zeilenenden, Commit.

Quest-IDs kommen aus den Schlüsseln (`qid('stone/goal')`): eine umbenannte oder verschobene Quest behält ihre ID,
solange ihr Schlüssel bleibt; der Fortschritt der Teams hängt an diesen IDs. Ein neuer Schlüssel ist eine neue Quest
(der Fortschritt beginnt bei null), ein gelöschter Schlüssel lässt die Quest verschwinden.

## 2. Wenn doch im Spiel editiert wurde

FTB Quests schreibt jede Änderung sofort in `<Server-Ordner>/config/ftbquests/quests/` (Einzelspieler:
`<Instanz>/config/ftbquests/quests/`). Das nächste Pack-Update überschreibt diese Dateien. Darum: Claude Bescheid
sagen, bevor das nächste Update den Server erreicht. Claude liest die Dateien (nur lesen), vergleicht sie mit dem
Repo (Texte in `lang/en_us.snbt`, Quests in `chapters/*.snbt`; die Formatierung von FTB Quests und die
`components`-Einträge an Items sind kein Unterschied) und zieht die Änderungen im Generator nach. Editor-Sprache dabei
Englisch (`ftbquests.xlate.editing_locale` = `en_us`), sonst landen die Texte in `lang/de_de.snbt`.

## 3. Die Regeln des Buchs

| Regel | Was passiert |
|---|---|
| Icon (Regel A, G4) | Das Icon einer Quest ist das Item, das die Quest selbst prüft oder gibt, nie ein späteres: bei Item-Aufgaben oder beobachteten Blöcken eines davon (oder ein Belohnungs-Item), sonst ein Item der Age des Kapitels oder früher (`dev/age_map.toml [derived]`). Lese-Quests wählen frei innerhalb der Age. Fehler „icon … is not an item the quest checks“ bzw. „is reachable in age_N, after …“. |
| Abhängigkeiten (Regel B, G4) | Eine Quest hängt nur von Quests ab, deren Aufgaben kein Item einer späteren Age verlangen (`[derived]`, auch kapitelübergreifend). Fehler „depends on … which asks for …“. |
| Machbarkeit (Regel C, G4) | Die Aufgaben einer Quest sind mit dem Stand ihrer Vorgänger machbar (keine Form ohne Grubenofen, kein Thatch ohne Messer). Das prüft kein Skript; der Generator vermerkt jede strangübergreifende Voraussetzung mit `deps=[...]` oder einem `trunk` (Pflichtschritte vor allen Strängen, Steinzeit). |
| Schrein-Ziel (G4) | Je Schrein-Age eine Ziel-Quest für Ring, Opfergabe und Ritus: drei Aufgaben (Beobachtung des Herzens mit `ready=true, awakened=N`, Opfergabe im Besitz, Stage der nächsten Age), drei Seiten Text (`shrine_goal`). Keine „Raise …“-Ringquests, keine Bauanleitungs-Diamanten: die Anleitung je Ring steckt in der Straßenmarkierung in The Firmament; ein Klick auf das Ringbild öffnet sie. |
| Items statt XP | Jede Quest, die Arbeit verlangt, gibt Items ihrer Age. Nur Lese-Quests (Tag `entry`, `explain`, `info`, `note`) geben XP und keine Items. |
| Keine XP neben Items | Eine Quest mit Item-Belohnung gibt keine XP dazu. |
| Belohnungen sind nicht Aufgaben-Items | Für jede Item-Aufgabe geben die Belohnungen anderer Quests derselben oder einer früheren Age höchstens ein Drittel der Menge; ein Aufgaben-Item einer Side Road zahlt keine fremde Quest; nie Age-Token, Opfergabe oder Chaos-Splitter; in Dawn keine Fackel. |
| Age-Erreichbarkeit | Belohnungs- und Aufgaben-Items gehören zur Age des Kapitels oder früher. Q-1 und Q-2 (`python dev/derive_ages.py --dump-dir <Dump>`, braucht einen Boot) prüfen die Erreichbarkeit über Rezept, Welt oder Beute. |
| Struktur | Jedes Age-Kapitel hat einen Eingang, ein Ziel, 3 bis 5 Keystones, 2 bis 6 optionale Nebenquests; Abhängigkeiten zeigen auf vorhandene Quests. |
| Text | Englisch nach `dev/style-guide.md` (Regel 6: keine Erklärtexte); Wortgrenzen je Questart, Beschreibungen mit `{@pagebreak}` je Seite bis 90 Wörter. |
| Karte | Quests nicht zu dicht (`dev/quest_layout.py` MIN_GAP), höchstens 2 Linienkreuzungen und 2 Linien über fremde Quests je Kapitel. Stellschrauben: `LAYOUT_HINTS` in `dev/gen_quests.py` (`order`, `related`, `style`). |
