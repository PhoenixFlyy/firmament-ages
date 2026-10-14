# Titelbild „Constellation“ (GLSL-Shader)

Der Titelbildschirm zeigt den ganzen Schrein (Ringe 0 bis 8) als 3D-Sternbild: Die Kamera kreist in 90 Sekunden einmal
herum, alle 120 Sekunden leuchten die Ringe nacheinander in ihrer Age-Farbe auf (Ring k bei 8 + 9k Sekunden), halten
bis 102 Sekunden, verblassen und beginnen neu. Dazu drei Sternschichten, Nebel und Funkeln. Kein Text, keine Maus.
Der Zyklus startet jedes Mal neu, wenn der Titelbildschirm aufgeht (`iTime` von FancyMenu).

## Dateien

| Datei | Inhalt |
|---|---|
| `shader_template.glsl` | Der handgeschriebene Shader mit Platzhaltern `@@...@@` für die Schrein-Daten. **Hier ändern.** |
| `build_shader.py` | Füllt die Platzhalter aus `dev/shrine-viewer/shrine_data.json` und schreibt `constellation.glsl`; trägt den Shader danach in das Layout ein. |
| `constellation.glsl` | Der fertige Shader (GLSL 150, Shadertoy-Einstieg `void mainImage`, 102 Knoten und 105 Kanten als `const`-Arrays). Erzeugt, nicht von Hand ändern. |

Im Pack steht der Shader nur in `config/fancymenu/customization/title_screen_layout.txt`, Block `menu_background`
(`background_type = glsl`), Eigenschaft `inline_shader_source`. FancyMenu 3.9.14 kennt keine Shader-Datei als Quelle,
der Text muss dort stehen.

## Neu erzeugen

```
python dev/shrine-viewer/render_shrine.py --data-root mod/firmages-core/src/main/resources/data/firmages
python dev/title-shader/build_shader.py
python dev/check_menus.py
```

1. Der erste Befehl schreibt `dev/shrine-viewer/shrine_data.json` (nicht in Git, etwa 30 s). Nur nötig, wenn sich
   der Schrein geändert hat oder die Datei fehlt.
2. `build_shader.py` reduziert die Schrein-Blöcke auf die Knoten-Arrays (jede erhöhte Säule, die Ecken und bei den
   Ringen 0 bis 3 die Kantenmitten des Grundrisses, das Herz als Knoten 0) und die Kanten (Grundriss-Schleife,
   minimaler Spannbaum über die Säulen, zwei Verbindungen zum Grundriss). Die Ringfarben kommen aus `beam_color`.
   Danach ersetzt es im Layout die Zeile `inline_shader_source = ...` durch den neuen Shader in einer Zeile.
   Nur den Shader neu eintragen (nach einer Handänderung an `constellation.glsl` zum Ausprobieren):
   `python dev/title-shader/build_shader.py --layout`.
3. `check_menus.py` prüft, dass der Wert im Layout zurückübersetzt genau `constellation.glsl` ist (siehe unten).

Benötigt Python mit `numpy`.

## Die eine Zeile im Layout

FancyMenu speichert mehrzeilige Werte in einer Zeile und ersetzt jeden Zeilenumbruch durch
`%%!serialized_property_newline!%%` (`PropertyContainer.serializePropertyValue`, beim Laden umgekehrt). Der Wert ist
deshalb rund 29 000 Zeichen lang; das ist normal.

Der Layout-Parser (`PropertiesParser.deserializeSetFromFancyString`) entfernt aus jeder Zeile alle Leerzeichen und
liest sie dann so: endet sie mit `{`, beginnt ein neuer Abschnitt; beginnt sie mit `}`, endet der Abschnitt; sonst ist
alles nach dem ersten `=` der Wert (ein führendes Leerzeichen fällt weg). Weil der Shader in einer Zeile hinter
`inline_shader_source =` steht, stören die Klammern im Shader nicht, solange die Zeile nicht mit `{` endet.
`build_shader.py` bricht in dem Fall ab, `check_menus.py` meldet jede Layout-Zeile, die der Parser falsch lesen würde.

`#version` und `precision` gehören nicht in den Shader: FancyMenu setzt selbst `#version 150`, die Uniforms
(`iTime`, `iResolution`, ...) und ein `main()`, das `mainImage(fragColor, fragCoord)` aufruft. Zeilen mit
`#version` oder `precision` im Shader würde es entfernen; `check_menus.py` meldet sie.

## Einstellungen im Layout

| Eigenschaft | Wert | Warum |
|---|---|---|
| `use_input` | `false` | Maus und Tasten erreichen den Shader nicht: keine Parallaxe. |
| `time_scale` | `1.0` | Zeit wie im Prototyp (Umlauf 90 s, Zyklus 120 s). Größer = alles schneller. |
| `show_compile_errors` | `true` | Für den ersten Test im Spiel: Lässt sich der Shader nicht übersetzen, steht der Fehlertext im Menü. Nach dem bestätigten Test auf `false` setzen. |
| `compile_mode` | `auto` | FancyMenu erkennt `mainImage` und nimmt den Shadertoy-Wrapper. |

## Stellschrauben im Shader

In `shader_template.glsl`: `ORBIT` (Sekunden pro Umlauf), `CYCLE` (Sekunden pro Age-Zyklus), `CAM_R` (Abstand),
`CAM_H` (Höhe), `FOCAL`, `uv.x -= 0.21` (Schrein rechts neben den Knöpfen), die Helligkeit der unbeleuchteten Ringe
(`0.30` Knoten, `0.11` Kanten), die Sterndichten in `sky()`. In `build_shader.py`: `Y_SCALE` (Höhenüberhöhung 1.9,
der Schrein ist 21 breit und 5 hoch).

Zwei Schalter stammen aus dem Prototyp und sind aus:

- `PHASE_FROM_CLOCK 1` lässt den Zyklus nach der Uhrzeit laufen (`fmUnixTimeSeconds`) statt ab Öffnen des Menüs.
  Nur sinnvoll, wenn wieder ein Age-Name als FancyMenu-Text erscheinen soll, der dieselbe Uhr liest.
- `DATA_FROM_TEXTURE 1` liest die Knoten aus einer Daten-Textur (`iChannel0`) statt aus den Arrays. Gedacht für einen
  Treiber, der große `const`-Arrays nicht schafft; auf NVIDIA siebenmal langsamer. Die Textur liegt nicht im Pack.

## Geprüft

- Der Shader übersetzt im selben Wrapper wie FancyMenu (gleiche Präambel, gleiches `main()`, moderngl auf der
  RTX 4080) und braucht dort 2,1 ms pro Bild in 1920x1080. Grobe Erwartung: 6 bis 8 ms auf einer RTX 3060, 30 bis
  50 ms auf einer Intel-iGPU. Er läuft nur, solange der Titelbildschirm offen ist.
- Im echten Spiel noch nicht geprüft.
