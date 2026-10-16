# Quest images: path, naming and use (Absprache quest-images / quest-engine)

Diese Datei ist die Absprache zwischen dem Bild-Generator (`dev/gen_quest_images.py`, Branch `b1/quest-images`) und
dem Questbuch-Generator (`dev/gen_quests.py`). Wer ein Bild braucht, das es noch nicht gibt, trägt es unter
„Wünsche“ ein; der Bild-Generator rendert es. Schrein-Bilder rendert nur eine Seite (siehe unten).

## Ablage und Namen

- Datei: `kubejs/assets/firmages/textures/quests/<group>/<name>.png` (KubeJS lädt `kubejs/assets` als
  Client-Resourcepack; der Server lädt keine Assets).
- Resource-Location im Questtext: `firmages:textures/quests/<group>/<name>.png`.
- Schlüssel im Manifest und im Helfer: `<group>/<name>`, zum Beispiel `knapping/axe_head`.
- Gruppen: `knapping` (Knapping-Raster), `device` (TFC-Geräte), `ponder` (Ponder-Vorschaubilder mit Klick),
  `build` (Bau-Stränge), `shrine` (Schrein-Ringe pro Age, nur auf Wunsch).
- Namen: Kleinbuchstaben, Ziffern, `_`; Englisch nach dem Spielgegenstand (`axe_head`, `pit_kiln`).
- Die Themen-Grafik (Hintergründe, Banner, Formen) bleibt in `textures/gui/quests/` (`dev/gen_quest_art.py`).

## Größen (Textur und Anzeige)

Jedes Bild wird auf einem logischen Raster gezeichnet und zweifach (nearest) in die Textur skaliert. Die
Anzeigegröße im Questfenster ist das logische Raster, so bleibt jedes Pixel bei jeder ganzzahligen GUI-Skala scharf.

| Gruppe | Textur (px) | Anzeige `width` x `height` (GUI px) |
|---|---|---|
| `knapping` | 128 x 128 | 64 x 64 |
| `device`, `build`, `ponder`, `shrine` | 256 x 256 | 128 x 128 |

Alle Texturen sind Zweierpotenzen, jede unter 256 KB, alle zusammen unter 10 MB (`--check` prüft beides). Das
Questfenster ist mindestens 200 GUI px breit (FTB Quests 2101.1.36 `ViewQuestPanel`: `max(200, Titelbreite + 54)`,
oder `min_width` der Quest bzw. `default_min_width` des Kapitels), 128 px passen also immer.

## Einbinden (FTB Quests 2101.1.36 / FTB Library 2101.1.37, im Jar geprüft)

Eine Beschreibungszeile, die nur aus dem Bild besteht:

    {image:firmages:textures/quests/knapping/axe_head.png width:64 height:64 align:center}

- Schlüssel (FTB Library `ClientTextComponentUtils`, `StringUtils.splitProperties`: durch Leerzeichen getrennt,
  `key:value` am ersten Doppelpunkt): `image`, `width` (Standard 100), `height` (Standard 100), `align`
  (`left`/`center`/`right`, Standard `center`), `fit` (`true` skaliert auf die Fensterbreite, dann nicht mehr
  pixelgenau), `click_action`, `text` (Hover-Text, wird wie Questtext geparst; Leerzeichen als `%20`).
- Ponder-Bilder tragen `click_action:custom_event:firmages:ponder/<namespace>/<path>` (firmages-core
  `QuestPonderLink`, SPEC §22.5): öffnet die Ponder-Szenen des Items, danach öffnet die Chronik wieder.
- In `dev/gen_quests.py` muss die Bildzeile ein eigener Eintrag bleiben: `Verbatim(...)` (sonst hängt
  `paragraphs()` sie an den Absatz davor).

Helfer ohne Abhängigkeit zu `gen_quests.py`: `dev/quest_image_refs.py`

    from quest_image_refs import image_line           # liest dev/data/quest_images.json
    Verbatim(image_line('knapping/axe_head'))         # {image:... width:64 height:64 align:center}
    Verbatim(image_line('ponder/portcullis'))         # mit click_action und Hover-Text
    image_line('build/silo_open', fit=True)           # optional fit

`image_line` wirft `KeyError` für einen unbekannten Schlüssel, so fällt ein Tippfehler beim Generieren auf.
`dev/validate_quests.py` prüft jede `{image:...}`-Zeile der Lang-Datei: Datei vorhanden, `width`/`height` Zahlen,
`click_action` mit bekanntem Präfix.

## Bilder (Stand: siehe `dev/data/quest_images.json`, das Manifest ist die Quelle)

Knapping (aus den ASCII-Rastern in `dev/gen_quests.py`, per `ast` gelesen, nicht abgeschrieben): Stein `axe_head`,
`knife_blades`, `hammer_head`, `shovel_head`, `hoe_heads`, `javelin_head`; Ton `small_vessel`, `ingot_molds`,
`pickaxe_head_mold`, `saw_blade_mold`, `jug`, `pot`, `bowls`, `large_vessel`, `hearth_idol`. Stein grau, Ton
rotbraun; stehende Felder hell, ausgeschlagene dunkel mit Kreuz.

Geräte (`device/`): `firepit`, `pit_kiln` (vier Schichten nebeneinander), `charcoal_pit`, `log_pile`,
`bloomery`, `blast_furnace`, `forge_anvil`, `quern`, `barrel`; mit kurzen Beschriftungen in Großbuchstaben.

Ponder (`ponder/`): `silo_hatch` (ad_astra/launch_pad), `portcullis` (create/rope_pulley), `lens_crown`
(firmages/star_anvil: „The Lens Crown“ und „A Chord Library“), `chords` (firmages/lens_mount); Standbild der
Ponder-Schematic mit Rahmen und „CLICK TO WATCH“.

Bau-Stränge (`build/`): `gatehouse`, `living_house`, `silo_closed`, `silo_open` (15 x 15 Luke).

## Schrein-Bilder

Der Bild-Generator rendert Schrein-Ringe nur, wenn der Questbuch-Generator sie hier unter „Wünsche“ anfordert
(`shrine/ring_<age>` fertig und `shrine/ring_<age>_steps` Schritt für Schritt). Wer sie rendert, steht dann hier.
Bis dahin rendert sie niemand in diesem Generator.

## Wünsche (quest-engine trägt ein)

| Schlüssel | Was | Status |
|---|---|---|
