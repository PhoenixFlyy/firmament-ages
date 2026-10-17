# Quest images: path, naming and use (Absprache quest-images / quest-engine)

Diese Datei ist die Absprache zwischen dem Bild-Generator (`dev/gen_quest_images.py`, Branch `b1/quest-images`) und
dem Questbuch-Generator (`dev/gen_quests.py`). Wer ein Bild braucht, das es noch nicht gibt, trägt es unter
„Wünsche“ ein; der Bild-Generator rendert es. Schrein-Bilder rendert nur eine Seite (siehe „Aufteilung“).

## Ablage und Namen

- Datei: `kubejs/assets/firmages/textures/quests/<group>/<name>.png` (KubeJS lädt `kubejs/assets` als
  Client-Resourcepack; der Server lädt keine Assets).
- Resource-Location im Questtext: `firmages:textures/quests/<group>/<name>.png`.
- Schlüssel im Manifest und im Helfer: `<group>/<name>`, zum Beispiel `knapping/axe_head`.
- Gruppen: `knapping` (Knapping-Raster), `device` (TFC-Geräte), `ponder` (Ponder-Vorschaubilder mit Klick),
  `build` (Bau-Stränge als beschriftete Diagramme). Schrein-Bilder: siehe „Aufteilung“.
- Namen: Kleinbuchstaben, Ziffern, `_`; Englisch nach dem Spielgegenstand (`axe_head`, `pit_kiln`).
- Die Themen-Grafik (Hintergründe, Banner, Formen) bleibt in `textures/gui/quests/` (`dev/gen_quest_art.py`).

## Größen (Textur und Anzeige)

Jedes Bild wird auf einem logischen Raster gezeichnet und zweifach (nearest) in die Textur skaliert. Die
Anzeigegröße im Questfenster ist das logische Raster, so bleibt jedes Pixel bei jeder ganzzahligen GUI-Skala scharf.

| Gruppe | Textur (px) | Anzeige `width` x `height` (GUI px) |
|---|---|---|
| `knapping` | 128 x 128 | 64 x 64 |
| `device`, `build`, `ponder` | 256 x 256 | 128 x 128 |

Alle Texturen sind Zweierpotenzen, jede unter 256 KB, alle zusammen unter 10 MB (`--check` prüft beides;
Stand: 32 Bilder, zusammen 88 KB). Das
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

Knapping (`knapping/`, Muster mit TFC-Rezept-IDs im Generator; jedes ASCII-Raster in `dev/gen_quests.py` wird per
`ast` gelesen und muss einem Bild entsprechen, sonst schlägt `--check` fehl): Stein `axe_head`, `knife_blades`,
`hammer_head`, `shovel_head`, `hoe_heads`, `javelin_head`; Ton `small_vessel`, `ingot_molds`, `pickaxe_head_mold`,
`saw_blade_mold`, `jug`, `pot`, `bowls`, `large_vessel`, `hearth_idol`. Stein grau, Ton rotbraun; helle Felder
bleiben stehen, dunkle werden ausgeschlagen (Bildunterschrift dann: "Light squares stay, dark squares are knocked
away." statt der X/-Legende).

Geräte (`device/`, nummerierte Teile und Legende in Großbuchstaben, ohne Titelleiste: der Questtitel steht darüber):
`firepit`, `pit_kiln` (vier Schritte als 2x2-Felder), `charcoal_pit` (Schnitt), `log_pile`, `bloomery`,
`blast_furnace`, `forge_anvil`, `quern`, `barrel`.

Ponder (`ponder/`): `silo_hatch` (ad_astra/launch_pad), `portcullis` (create/rope_pulley), `lens_crown`
(firmages/star_anvil: „The Lens Crown“; „A Chord Library“ hängt seit dem Review 0.13.0 am AE2-Pattern-Provider),
`chords` (firmages/lens_mount); Standbild der Ponder-Schematic von Nordwesten wie Ponder, Titel oben, „CLICK TO
WATCH“ unten, Hover „Click to watch“. Dazu Standbilder aus Creates eigenen Szenen für die Schritte der Großbauten
(Review 0.13.0): `elevator` (create/elevator_pulley), `display_link`, `clockwork_bearing`, `mechanical_bearing`. Ihre
Schematics liegen als Blocklisten in `dev/data/create_ponder_plans.json` (`--refresh-ponders --mods <dir>` liest sie
aus dem Create-Jar), damit `--check` ohne Jar läuft.

Bau-Stränge (`build/`): `gatehouse` (in Questreihenfolge: Seilbrücken und Graben, Fallgitter, Zugbrücke, Signal),
`living_house` (Schiebetüren, Lampen an einem Link, Anzeigetafel, Aufzugsschacht, Uhr im Giebel; die Garage ist
weg), `silo_closed`, `silo_open` (15 x 15 Luke, Taschen 7 und 8 breit, Schnitt durch den Schacht), als beschriftete
Teile-Diagramme.

## Aufteilung mit dem Questbuch-Generator (Stand 2026-10-16 08:30)

Der Questbuch-Zweig (`b1/quest-engine`) rendert selbst, mit echten Block-Texturen aus den Jars
(`dev/gen_quest_pictures.py`, `dev/qart_iso.py`, `dev/qart_textures.py`):

- `textures/gui/quests/shrine/ring_N*.png`: Schrein-Ring pro Age, Schritt-Bilder pro Schicht und APNG-Aufbau.
  **Schrein-Bilder rendert nur der Questbuch-Zweig**; dieser Generator rendert keine (Gruppe `shrine` entfällt hier).
- `textures/gui/quests/works/*.png`: die drei Großbauten als texturierte Ansicht (fertiges Bild).

Damit es keine zwei Bilder für dieselbe Aussage gibt, gilt: `works/` ist das fertige Bild (Eingang oder Ziel eines
Bau-Strangs), `build/` hier das beschriftete Teile-Diagramm für die Schritt-Quests (was wo sitzt). Ponder-Klicks
gehören auf die `ponder/`-Bilder dieses Generators, nicht auf `works/`.

## Einbindung (Integration H3+B1, 2026-10-16)

Alle 32 Bilder stehen in Quests (`dev/gen_quests.py`): `knap_grid()` gibt das Knapping-Bild seines Musters aus (die
Zeilen im Aufruf bleiben die Prüfquelle für `--check`; ein Muster ohne Bild bricht den Generator ab); `device/` in
Before Dark, Potter, A Better Fuel, Forging, The Grind, Ironworks, Blast Furnace und Barrel; `ponder/lens_crown` und
`ponder/chords` in „The Lens Crown“, `ponder/portcullis` und `ponder/silo_hatch` in den Werk-Quests; `build/` auf
Seite 2 der Werk-Einstiege, `build/silo_open` in „The Shaft“. Die Engine-Bilder (`works/`) zeigen Karte, Einstieg
und Ziel. Die Ponder-Klicks hängen auch an den `works/`-Bildern der Engine (sie öffnen dieselben Szenen).

## Wünsche (quest-engine trägt ein)

| Schlüssel | Was | Status |
|---|---|---|

## Quest-Ansicht und Ember-Blur (G1, 2026-10-17)

Felix nach 1.0.0: der Hintergrund der einzelnen Quest war verwaschen und gekachelt. Ursache: `panel.png` (64 px
Value-Noise, nicht nahtlos) als `ImageIcon` mit `tile_size=64`; die FTB-Library-Engine wiederholt so eine Textur alle
`tile_size` GUI-Pixel ab dem Bildschirmursprung (nearest, ohne mcmeta), bei GUI-Skala 2 also 2x vergroessert mit Naht
alle 128 Bildschirmpixel. Drei Mockups in echter Groesse (GUI-Skala 2, Minecraft-Schrift aus `ascii.png`):
`dev/quest-preview/panel_mockup_1.png` (flache Flaeche Nachtblau 95 %), `_2.png` (nahtloses feines Korn 128 px,
`tile_size=128`), `_3.png` (Vignette, ohne `tile_size` ueber das Fenster gestreckt). Gewaehlt: 1, die flache Flaeche
`#F2101A3E` im Goldrahmen fuer `quest_view_background`, `context_menu`, `chapter_panel_background` und
`key_reference_background` (`kubejs/assets/ftbquests/ftb_quests_theme.txt`); nichts kachelt oder verschwimmt bei
irgendeiner Skala, das Korn von 2 war bei dieser Dunkelheit unsichtbar bis mulmig, die Vignette von 3 wirkt weich.
`panel.png` und `panel_texture()` in `dev/gen_quest_art.py` sind weg.

`shapes/ember/*.png.mcmeta` behalten `blur: true`: die 128-px-Form auf 24, 32 und 48 px einmal GPU-bilinear (ohne
Mipmaps) und einmal nearest gerendert; bei 24 px sind beide gleich stufig, bei 32 und 48 haelt linear den Innenring und
den Rand etwas glatter, nearest zerlegt den Innenring in Striche. Kommentar an der mcmeta-Zeile in `gen_quest_art.py`.

Bild des fertigen Schreins: `textures/gui/quests/shrine/complete.png` (432 x 266, alle Ringe in ihren Farben, Herz und
Plinth) aus `dev/gen_quest_pictures.py` (`complete_picture`), Groesse und Blockzahlen unter `"complete"` in
`dev/data/quest_pictures.json`. Beim Neurendern in dieser Umgebung bekamen `ring_4` bis `ring_8` die Item-Sprites der
Laternen und Flutlichter statt blasser Ersatzwuerfel (die Jars der Instanz waren erreichbar); die Bilder sind mit
regeneriert.

