# Client-Test

## Runde H3 Endspiel (2026-10-16, Zweig h3/endgame, für den Integrator)

Server-Teil auf einer Kopie geprüft (`dev/poc-results.md`, Abschnitt „1.0 round H3, end game“): GameTests 122 von
122, `selftest all` 106 von 106 (neu: Suite `endgame`), `poc_analyze.py` alle PASS (age_9 6,0x), `check_log.py` 0 übrig.

1. **Erzprozessor (Singularitätszeit).** Block setzen, Erzstücke mit Rechtsklick hinein, Kerosin- und Schmiermittel-Eimer
   hinein, Fluorwasserstoffsäure per Mekanism-Leitung, Strom per Kabel. Jade zeigt „Processing: 21 Iron Dust, 45 %“
   (bzw. „Processing, lubricated: …“), die FE-Zeile und je Tank eine Zeile; leere Hand: dieselbe Zeile in der
   Aktionsleiste; Schleichen + Rechtsklick nimmt den Staub. Die Seiten leuchten animiert, solange er arbeitet. EMI:
   Kategorie „Ore Processing“ erst ab der Singularitätszeit, Säure als Text, Kerosin, Schmiermittel als Katalysator.
2. **Arkan-Katalysator (Informationszeit).** An eine Mekanism-Anreicherungskammer setzen (die Rune schaut auf die
   Kammer), eine Welle mit 64 RPM daneben, ein Source-Gefäß im Umkreis 6, Strom an die Rückseite: die Kammer arbeitet
   sichtbar schneller (etwa +50 %), Jade „Quickening the machine it faces: 50 % faster“, Source-Partikel vom Gefäß, die
   Front leuchtet. Ohne Welle „Needs a shaft turning at 64 RPM beside it“, ohne Source „Waiting for Source …“. Dasselbe
   an einem IE-Brecher (an irgendein Teil gesetzt).
3. **Sternentor.** Das Wählen von The Origin braucht 1 G FE im Tor (ein Interface, das der Reaktor speist); mit
   weniger kommt „not enough power“. In The Origin heimwählen geht immer (das Rücktor hält eine Heimreise).
4. **Händler.** Ein Waffen- oder Rüstungsschmied, der Eisenbarren kauft, verlangt Schmiedeeisen-Barren (Handelsfenster).

## 0.12.0 Review-Fixes (2026-10-16, Pack 0.12.0, firmages-core 0.12.0 neu gebaut)

Server-Teil auf einer Kopie geprüft (`dev/poc-results.md`, Abschnitt „0.12.0 review fixes, round C2“): GameTests
115 von 115, `selftest all` 101 von 101, `poc_analyze.py` alle PASS (neu W-9), `check_log.py` 0 übrig. Hier nur, was
ein Client zeigt; Punkt 8 und 11 des Nachtrags darunter gelten mit den neuen Zahlen.

1. **Dawn-Hinweis.** In Dawn E drücken: in der grauen Fläche steht umgebrochen „No grid yet. V: lay items on a block.
   Sneak + Right Button with an empty hand: craft.“ (Tasten aus der Belegung), lesbar, nichts über Spielermodell oder
   Rand; beim ersten Öffnen der Sitzung derselbe Satz einmal in der Aktionsleiste. Im Curios-Bildschirm ebenso.
2. **Tally Chute (jetzt Industriezeit).** Einen Trichter mit Stahlbarren darauf: die Chute nimmt nur, was der Nachweis
   noch will, und behält es; Jade „Takes 384 more Steel Ingot for Caelum“, „Counted: …“, nach Erfüllung „Takes nothing
   now“ und der Trichter behält den Rest; Rechtsklick mit Barren in der Hand wirft ein. Ziel-Quest und Herz nennen 384.
3. **Questbuch.** Strang „Sky“ von der Eisen- bis zur Singularitätszeit (Layout mit fünf Strängen, Belohnungen der
   Keystones), Arkanzeit mit „The Spirit Route“ und „The Machine Route“: die Ziel-Quest öffnet, sobald eine Route fertig
   ist; „Proof of Work“-Nebenquests ab der Industriezeit, „Energy Upgrades“ in der Informationszeit.
4. **EMI-Infoseiten.** Sturmglas, Firmament-Kristall, Polarlicht (Eimer und Fluid), Voidglass, Deep-Space-Probe,
   Meteoreisen- und Sternenstahl-Brocken: Taste U zeigt eine Seite mit der Quelle; Meteorit und Starfall-Kern stehen ab
   der Eisenzeit in EMI, der Sternenstahl-Meteorit ab der Elektrozeit. Das Client-Skript `viewer_info.js` lief noch in
   keinem Client (Server ohne KubeJS-Fehler, Syntax geprüft): fehlt die Seite, das KubeJS-Log des Clients ansehen.
5. **Polarlicht.** Im Kälteband steht jede klare Nacht Polarlicht, und das Netz fängt (Jade „Catching the aurora“),
   im kalten Halbjahr doppelt so schnell; nie „No aurora overhead“ unter sichtbarem Polarlicht des Auroras-Mods.
6. **Inseln.** Wie Punkt 4 des Nachtrags; erwartet nach den Jars: Jetpack ohne Höhengrenze (Luft für 450 s), die Hülle
   eines Heißluftschiffs muss auf Inselhöhe etwa doppelt so groß sein wie am Boden (Sable-Luftdruck 0,43 bis 0,47).
   Schafft eins von beiden die Inseln nicht, ist das ein Blocker für die Industriezeit (Cloudsteel).
7. **Wind Shrine und Ruinen.** Keine Gitter mehr in den Ruinen von Swamp Altar, Mountain Spire und Wind Shrine, die
   Reef-Altar-Ruine mit Kalkstein statt Korallen; nach dem Wiederaufbau mit Gittern und Korallenblöcken leuchten sie.
8. **Digital Miner** mit 4.800 FE/t: fühlt sich die Weltraumzeit damit richtig an?

## 0.12.0 Nachtrag (1.0 Runde C2 2026-10-16, Pack 0.12.0, firmages-core 0.12.0)

**Neue Welt empfohlen, Profil neu importieren** (Client und Server brauchen firmages-core 0.12.0, Netzwerk-Protokoll 6).
Vanilla-Haine und die dichteren Wayshrines entstehen nur in neuen Chunks; eine alte Welt läuft weiter (Inseln kommen
dort als ein Cluster nahe dem Schrein ab der Industriezeit, The Origin bekommt ihre Sockel nur in Luft). Server-Teil auf
einer Kopie des Test-Servers geprüft (`dev/poc-results.md`, Abschnitt „1.0 round C2 (2026-10-16)“): jeder Boot mit neuer
Welt, alle Ages per Simulation, `poc_analyze.py` 121 PASS und 0 FAIL, `check_log.py` 0 übrig, GameTests 115 von 115
(zwei Läufe), `selftest all` 101 von 101 (Crown 6 von 6). Hier nur, was nur ein Client zeigt; die Punkte zum
Dawn-Raster stehen im Abschnitt darunter („Dawn ohne 2x2-Feld“). Ältere Abschnitte, die Vanilla-Holz erst in der
Singularitätszeit oder gar nicht nennen (0.10.0 Punkt 1, n7-Deko Sichtprüfung Punkt 4), gelten nicht mehr: Vanilla-Holz-Deko gibt
es jetzt in den Stilen, die auch eine TFC-Holz-Kopie haben, in deren Age.

1. **Dawn.** Abschnitt „Dawn ohne 2x2-Feld“ unten, fünf Punkte. Dazu die neue erste Erklär-Quest „By Hand“.
2. **Holzklassen.** Einen wilden TFC-Baum fällen: es fallen Vanilla-Stämme seiner Klasse (Jade nennt die Art). Säge +
   Vanilla-Stamm = 2 Bretter, Säge + Bretter = Bretter-Holz (Lumber) der Leitart, 4 Vanilla-Bretter = Werkbank; Quest
   „Workbench“ zählt sie. „Wood Classes“ lesen; acht Klassen-Stämme um einen Art-Setzling = acht Stämme der Art.
   Vanilla-Bäume in Wäldern (Eiche, Birke, Fichte, Tropen, Akazie, Schwarzeiche, Kirsche, Mangrove im Salzmarsch): sehen
   sie neben TFC-Bäumen fremd aus, stört Laub ohne Jahreszeiten? In EMI ab der Steinzeit Vanilla-Holz-Deko nur in
   Stilen mit TFC-Kopie.
3. **Sky Harvest.** Eisenzeit: Sturmstab bauen (zwei Blöcke hoch), Fenster (16 Sand, 16 Glas), im Regen ein Blitz ab
   und zu, leiser Donner nur in etwa 64 Blöcken; Sturmglas als verbundenes Glas (Fusion). Industriezeit: Aurora-Netz
   auf Y 140 oder höher im kalten Norden (`/firmages sky aurora band` als OP nennt die Richtung), nachts Schleier im
   Zustand `catching`, Polarlicht-Fluid im Tank, Jade-Zeilen an Stab und Netz. Passt das Leuchten zu den Auroras der
   Mod Auroras?
4. **Drifting Isles.** Ein Heißluft-Luftschiff der Industriezeit von Y 90 auf Y 275 und zurück (wie groß muss die
   Hülle sein?), ein Jetpack-Aufstieg von Y 120 auf Y 240; die Inseln und das Leuchten der Cluster nachts vom Boden;
   Skystone-Licht und Schatten (Face Culling); Lode abbauen: Klang, steht sofort wieder, Kristalle; die Wind Shrine auf
   einer Insel des Clusters (`/firmages isles locate` als OP).
5. **Lens Crown und Legierungen.** Arkanzeit: Star Anvil im Steinring mit drei Lens Mounts, Akkord der Linsen,
   Starlit Iron schmieden (Ponder-Szenen, EMI-Kategorie). In EMI je Age genau eine neue Legierung, und die
   Signatur-Rezepte zeigen sie: Arcane Keystone (Deployer-Schritt), Pressure Core (zwei Cloudsteel), Humming Core
   (Aurora Steel), Data Matrix (zwei Halo Steel), Star Chart (Void Steel), Quantum Core (Quasar Alloy), Ultimate
   Singularity (Firmament Alloy). Wayshrine-Opfergabe: Arkan Starlit Iron, Industrie Cloudsteel, Elektrik Aurora Steel,
   ab Information Halo Steel.
6. **Startkomplex (Raumfahrtzeit).** Geist-Vorschau mit leerer Hand an der Steuerung deckt sich mit dem echten Bau;
   Start aus 200 Blöcken (Flammenspur, Graben-Strahlen, Abstieg unter drei Schirmen); eine Create-Silo-Luke öffnet beim
   Countdown (Redstone hinten); Dieseleimer per Hand in die Steuerung (mit freigeschalteter Age); Rückkehr mit
   Voidglass; Platzhalter-Texturen und das Sondenmodell.
7. **Sky Anchor.** Anker auf dem Mond setzen, Splitter einer erweckten Stufe einlegen: Segen im Umkreis von 24 Blöcken,
   Jade-Zeile; nur Splitter nicht erweckter Stufen: Jade „Quiet …“.
8. **Arbeitsnachweis am Schrein.** Ab der Industriezeit: Opfergabe ohne Zählung wird abgelehnt und bleibt in der Hand,
   Caelum nennt die fehlende Zahl (seit den Review-Fixes: 384 Stahl, dann 512 Aluminium, 1.024 Osmium, 2.880 Desh,
   3.584 Calorit, in Tally Chutes eingeworfen in dieser Age); Jade am Herz zeigt die Zählung, die Ziel-Quest nennt sie. Data Nave: 10 Mio. FE ins
   Herz, Quantum Ring 100 Mio., die Gathering 1 Mrd.
9. **The Primordial.** Kampfdauer mit 1, 2 und 4 Spielern stoppen gegen das Modell 8,4 / 7,2 / 6,5 Minuten; ein Treffer
   macht höchstens 40 Schaden (auch mit dem Draconic-Bogen).
10. **Archipel.** Nach dem Sieg eine Insel wachsen lassen (Stufen 1 bis 3, vom Sockel nach außen), Strahl am Altar in
    der Farbe der Insel, nach der neunten Insel 10 s FINALE-Himmel; Sockel-Textur; Steininsel mit befeuerter
    TFC-Feuerstelle als Arbeit.
11. **Balance vorher/nachher** (Zahlen aus `dev/harmonie-r2.md`): Brechräder 1,7 → 1,5x, IE Crusher 2,0 → 2,5x plus
    Mineralpulver, Lichtbogenofen Staub → Barren 51.200 → 6.400 FE, Mekanism-Maschinen 10-facher Verbrauch, Digital
    Miner 4.800 FE/t (Review-Fix; vorher 36.000) und 10 s je Block ohne Upgrades, Excavator halbes Tempo, Vanilla-Eisenbarren weg (Eisenstaub im
    Energized Smelter gibt Schmiedeeisen?), modularer Dieselmotor 1,68 → 1,31 Mio. FE je Eimer. Fühlen sich Miner und
    Mekanism in der Weltraumzeit zu teuer oder zu langsam an?

## Dawn ohne 2x2-Feld (Runde C2 2026-10-16, Zweig c2/gridlock, in 0.12.0)

Felix' HW2A (SPEC §19.3): das 2x2-Feld im Inventar ist nur in Dawn zu, es öffnet mit „The First Spark“ (age_0), wie
die Handabbau-Regel. Client und Server brauchen dasselbe firmages-core (Netzwerk-Protokoll 6). Neue Welt, als
Überlebens-Spieler ohne OP (OP nur für die Befehle).

1. **Inventar in Dawn.** E öffnen: kein 2x2-Feld, kein Pfeil, kein Ergebnisfeld, kein Titel „Crafting“, kein
   Rezeptbuch-Knopf; die Fläche ist glatt grau, Spielermodell und Rüstungsplätze wie immer. Mausrad und Ziehen mit
   Mouse Tweaks über der Fläche legen nichts ab. Dasselbe im Curios-Bildschirm (Knopf am Inventar).
2. **EMI als OP.** In Dawn ein Rezept in EMI öffnen: das „+“ ist grau, Shift-Klick legt nichts ins Feld. Falls
   irgendwo eine JEI-Oberfläche mit eigenem „+“ auftaucht: notieren (der Server legt trotzdem nichts ab).
3. **Bodenhandwerk.** Steinaxtkopf knappen, Kopf in die Hand, V auf einen Block, Stock in die Hand, V auf denselben
   Block. In der Aktionsleiste steht einmal „Sneak and right-click with an empty hand: Stone Axe“ (danach höchstens
   einmal pro Minute). Leere Hand, Strg halten, Rechtsklick auf den Haufen: Steinaxt im Inventar, Haufen weg, Ton und
   ein paar Krümel. Ebenso: vier Stroh = Stroh-Block (Thatch), zwei Stöcke = Feuerstarter. Ein Haufen, der nichts
   ergibt (ein einzelner Kopf), gibt wie bisher ein Teil zurück; ohne Strg nimmt Rechtsklick wie bisher ein Teil.
   Wichtig: kein kurzes Flackern (Teil verschwindet und kommt wieder) beim erfolgreichen Rechtsklick.
4. **Freischaltung.** `/stage grant @s age_0` (oder The First Spark spielen): im Chat „Your hands have learned: the
   crafting grid is open (E).“; Inventar neu öffnen, das Feld ist da und macht aus vier Stroh wieder Thatch. Ein
   offenes Inventar zeigt das Feld schon beim nächsten Bild. Bodenhandwerk geht weiter.
5. **Sophisticated Backpacks.** Rucksack öffnen: kein 2x2 im Rucksack-Bildschirm (nur das Upgrade, das bleibt).

## 0.11.0 Nachtrag (1.0 Runde C1 2026-10-16, Pack 0.11.0, firmages-core 0.11.0)

**Voller Reset:** neue Welt und neu importiertes Profil (Client und Server brauchen firmages-core 0.11.0, Netzwerk-
Protokoll 5). Wayshrines und alte Krater entstehen nur in neuen Chunks. Server-Teil auf einer Kopie des Test-Servers
geprüft (`dev/poc-results.md`, Abschnitt „1.0 round C1 (2026-10-15)“): jeder Boot mit neuer Welt, alle Ages per
Simulation, `poc_analyze.py` 104 PASS und 0 FAIL, `check_log.py` 0 übrig, GameTests 81 von 81, `selftest all` 71 von 74
(drei Lens-Crown-Prüfungen rot bis M15, gewollt). Die Einzelprüfungen der Zweige stehen in den zwei Abschnitten
darunter („C1-Deko“, acht Punkte, und „Technik- und Welt-Mods“, acht Punkte); hier nur, was dazukommt.

1. **Neue Mods nach Age** (in EMI nur ab ihrer Age, als OP alles sichtbar):
   - Dawn: A handful of acorns (Nüsse im Herbst), KleeSlabs, Goblin Traders (Höhle unter Y 40), Alex's Mobs Continued
     (22 Arten). Stein: Armor Statues, Banner Flags. Bronze: Connected Glass, Advanced Chimneys, To the Bat Poles!,
     Artifacts (Ruinenkisten). Eisen: Bibliocraft Legacy, MmmMmmMmmMmm, Hebestange. Arkan: Ars Elemental, Ars Ocultas,
     Akashic Tome, Enchantment Descriptions. Industrie: Create: Extra Gauges, das teure Wireless Crafting Terminal.
     Elektrik: AeroEngine, Building Gadgets 2, Ars Elemancy. Information: Psi, Ars Énergistique. Singularität:
     Draconic Additions (drei Halsketten). Nur Client: 3D Skin Layers, Auroras, Cool Rain, BetterF3, Chat Heads,
     Big Sign Writer.
   - Weg sind Carry On (Shift-Rechtsklick hebt keine Kisten und Tiere mehr) und Furniture: Refurbished (keine Küchen,
     Computer, Fernseher; auch nicht in EMI).
   - Melden: Chat-Zeilen oder Fenster beim Beitreten, fehlende Texturen, Items vor ihrer Age.
2. **The Primordial.** Testwelt mit 1 bis 4 Spielern: `/stage grant <Spieler> age_9`, Gate bauen oder
   `/firmages origin tp <Spieler> force`, alle in 6 Blöcken um den Altar. Ansehen: Modell (Glas, Kiel, Halo aus neun
   Lichtern), Animationen, Bossleiste, Titel der drei Phasen, Klänge mit Untertiteln, Laternen gehen in Phase 2 aus
   und kommen beim Tod zurück, Sternfall mit goldener Markierung, Splitter (Benutzen: Firmament Ward; Nebenhand +
   Schlag; Schleichen + Benutzen: Wurf; der Tooltip nennt die drei Wege). Dauer stoppen: Ziel 6 bis 10 Minuten mit 2 bis 4 Spielern. Danach Titel
   „Beyond the Firmament“, goldener Schreinstrahl, MekaSuit baubar. Die Erklärung „The Primordial“ im Chronicle lesen;
   „A Piece of the Sky“ schließt mit einem Splitter ab. Wipe testen: alle sterben, nach 30 s zieht es sich zurück.
3. **Starfall.** Ab der Eisenzeit in einer klaren Nacht (`/firmages starfall now` als OP, oder `/firmages starfall
   tonight` und warten): Streifen am Himmel in die echte Richtung, Knall auch aus 1.000 Blöcken, Actionbar „A star falls
   to the …“, Rauchsäule, Krater mit glühendem Kern (mit Sodium und Complementary: leuchtet der Kern?), TFC-Erdrutsche
   am Kraterrand. Meteorit abbauen: Meteoreisen-Brocken, doppelt solange der Kern glüht (Jade zeigt die Restzeit).
   Meteoreisen erhitzen und auf dem Amboss schmieden; Sternenstahl ab der Elektrikzeit im Lichtbogenofen. Vor der
   Eisenzeit muss ein Meteorit wie Himmelsgestein aussehen und nur Gestein geben.
4. **Wayshrines.** Neue Welt: Rechtsklick auf das Schrein-Herz nennt Richtung und Entfernung der nächsten (die
   Tutorial-Wayshrine liegt etwa 400 Blöcke vom Spawn). Am Kern: Geist der fehlenden Blöcke, Jade „Ruined: … missing“.
   Wiederaufbau aus dem Gestein der Umgebung, Opfergabe der Age (Bronze: Bronze-Doppelbarren; Eisen: Meteoreisenbarren;
   Arkan: Meteoreisen-Doppelbarren; Industrie: Meteoreisen-Blech; ab Elektrik: Sternenstahl-Barren), Ritus, Gebet.
   Danach Strahl, Wayshrine Stone fällt, Segen im Umkreis von 24 Blöcken (Segen nur noch am Schrein und an
   Wayshrines, nicht mehr überall). Auf einer alten Welt (älter als drei Tage) entsteht keine Tutorial-Wayshrine.
5. **Lens Crown.** Nicht im Spiel: Star Anvil, Lens Mount, Linsen und Legierungen sind versteckt und ohne Rezept, bis
   M11 bis M15 kommen. Als OP in EMI nachsehen, dass keines davon im Überleben auftaucht.
6. **Balance vorher/nachher** (im Spiel nachmessen, wo es geht):
   - Alternator bei 256 RPM: 360 → 168 FE/t (`generator_efficiency` 0,75 → 0,35), aber nur aus befeuerter Drehung:
     Dampfmaschine über einem Blaze Burner mit Brennstoff (oder Dieselmotor) gibt 168 FE/t, Wasserrad oder Windmühle
     allein 0, beides an einer Welle den befeuerten Anteil (`/firmages balance alternator <pos>` als OP zeigt Quellen
     und Anteil). Lava im Blaze Burner: aus.
   - Huge-Dieselmotor: 0,05 → 0,11 mB/t; jeder Dieselmotor gibt 1,3 bis 1,7 Mio. FE je Eimer (IE-Generator 1,32 Mio.).
     Pflanzenöl und Ethanol laufen nicht mehr im Dieselmotor.
   - Weg: Crystal Resonance Generator, Applied-Kinetics-Energy-Provider, Solar-Upgrade von Evolved Mekanism.
     Thermoelektrischer Kessel, Industrieturbine, Fuelwood- und Resistive Heater: Information → Raumfahrt.
   - Mystical-Agriculture-Metall-Essenz: 8 Essenz → 2 normale TFC-Erzstücke (vorher Barren). HNN gibt Erzstücke.
   - Theurgy Digestion Vat: genau 2,0x auf jedem Grad (10 reiche Stücke → 7 Schwefel, 2 normale → 1).
   - Waschen von Kies, Seelensand und rotem Sand: keine Nuggets mehr. End-Draconium: 2 reiche Stücke statt Staub;
     Planetenerze: reiche TFC-Stücke.
   - AE2: 32 Kanäle je Kabel, 128 je dichtem Kabel (vorher 8 und 32); Item-Zellen 126 Typen statt 63 (Tooltip
     einer 64k-Zelle; tragbare Zellen unverändert). Wireless Crafting Terminal ab der Industriezeit (Mechanical Crafter, teuer): unverlinkt öffnet
     Rechtsklick ein 3×3-Crafting-Raster (Rezept legen, Ergebnis nehmen, schließen: die Zutaten kommen zurück);
     verlinkt (Wireless Access Point, Information) AE2s Terminal.
   - Excavator: Banded Iron, Lazulitic Intrusion und Rich Auricupride (Badlands) entstehen jetzt in TFC-Biomen,
     Alluvial Sift und Super Vein an TFC-Flüssen und -Seen (Core Sample).
7. **Messroutine Client** (wie Abschnitt „n7-Deko“, Messung, mit A = 0.10.0 und B = 0.11.0, je drei Läufe, dieselbe
   Instanz und RAM): Startzeit bis Titelbild, erster Einstieg, Heap nach `/sparkc heapsummary`, Blockatlas-Größe,
   mit `python dev/check_log.py --client <log>` auswerten. Zusätzlich in B: Bildzeit in einer Nacht mit Auroras und
   Complementary (`/sparkc profiler` 60 s) und beim Blick auf einen glühenden Krater. Ergebnis in `dev/poc-results.md`
   als „0.11.0 client measurement“.

## C1-Deko (1.0 Runde C1 2026-10-15, Zweig c1/mods-deco, für den Integrator)

16 Mods aus der Inhaltssperre plus die Bibliotheken ForgeEndertech, Bookshelf und Prickle; Carry On und Furniture:
Refurbished sind raus. Server-Teil auf einer Kopie mit neuer Welt geprüft: `poc_analyze.py` 98 PASS, `check_log.py`
0 offen (nur „Can't keep up“ beim Erzeugen der neuen Welt), `selftest all` 55 von 55. Im Client noch offen:

1. **BetterF3 in der Steinzeit:** F3 zeigt keine XYZ-, Block- und Chunk-Koordinaten, nur „Chunk-relative“ wie Vanilla.
2. **Ein Polarlicht:** in kalter Breite (Norden, negatives z) bei klarer Nacht nur das Polarlicht von Auroras, keins
   von Complementary (neues Profil, damit defaultoptions die Shader-Einstellung schreibt).
3. **Banner Flags:** Banner schleichend an einen Zaun oder eine Mauer setzen, die Flagge weht; beim Beitritt kein
   Toast „Gurkis Datapacks“.
4. **Bibliocraft (Eisenzeit):** Vitrine aus TFC-Holzstufe, Etikett, Klemmbrett, Lagerkatalog, Bücherregal und edles
   Schild in TFC-Holz; Sitze, Tische, Regale, Lampen, Uhren und Werkzeugregal fehlen in EMI.
5. **Kamine:** Rauch einer TFC-Feuerstelle steigt durch einen Kamin darüber, ohne Vergiftung.
6. **Akashic Tome (Arkanzeit):** TFC-Feldführer, Schrein-Buch und Occultism-Buch lassen sich einlegen und wechseln.
7. **Client-Mods:** 3D Skin Layers, Cool Rain, Chat Heads, Big Sign Writer (Schild mit großer Schrift),
   Enchantment Descriptions (Tooltip auf verzaubertem Buch) laufen ohne Meldung beim Start.
8. **Bat Poles:** Holzstange in der Bronzezeit, die Hebestange erst in der Eisenzeit.
## Technik- und Welt-Mods (Runde C 2026-10-15, Zweig c1/mods-tech, für den Integrator)

14 neue Mods (Zeilen „Runde C“ in `dev/decisions-while-away.md`). Server-Teil auf einer Kopie geprüft
(`dev/poc-results.md`, Abschnitt „1.0 round C, mods-tech“). Im Client prüfst Du nur, was ein Server nicht zeigt:
Texturen, Bildschirme, Tooltips, Spawns in echter Welt. Neue Testwelt; die Ages vergibst Du wie bei den früheren
Tests.

1. **EMI pro Age.** In Dawn sind Goblin Traders und die 22 Alex's-Mobs-Arten sichtbar (Fleisch, Eier), kein Artifacts,
   kein Psi, kein Gadget. Nach `age_6` sind Psi, Ars Énergistique und die Source-Zellen da; die ME-Source-Akzeptoren,
   Copy-Paste-, Cut-Paste-Gadget und Template Manager, AeroEngine-Raketen, Chaos-Teile von Draconic Additions und die
   Alex's-Mobs-Items der gestrichenen Arten (Bärenfell, Bananenschnecke, Kakerlake …) erscheinen nie.
2. **Alex's Mobs in TFC-Klima.** Eine warme, trockene Gegend (Savanne, über 18 °C) suchen: Elefanten, Nashörner,
   Kängurus, Emus. Ein kalter Wald: Waschbären und Blauhäher, keine Tukane. Melden, wenn eine Art im falschen Klima
   steht oder gar keine der 22 Arten zu sehen ist.
3. **Goblin-Händler.** In einer Höhle unter Y 40 einen Goblin finden (oder `/summon goblintraders:goblin_trader`):
   seine Angebote sind TFC-Waren der aktuellen Age, keine Schatzkarte.
4. **Artifacts.** Eine Ruinenkiste nach `age_1` öffnen; ab und zu liegt ein Trinket darin (Hüte, Schal, Schnorchel).
   Tooltips und Curios-Slots anschauen.
5. **Psi.** Nach `age_6`: Encyclopaedia Psionica aus Buch und Psistaub (Rezept in EMI), CAD-Assembler mit zwei
   Grundschaltkreisen, ein CAD bauen, im Programmer einen einfachen Zauber setzen. Prüfen, dass das Buch aufgeht
   und der Programmer-Bildschirm vollständig ist.
6. **Building Gadgets 2.** Nach `age_5`: Baugadget laden (FE), eine Reihe Bretter setzen; das Tauschgadget an einem
   TFC-Erz und an Rohgestein versuchen (es muss sich weigern).
7. **AeroEngine.** Ponder des Triebwerks ansehen; Flugkerosin aus IP-Kerosin im beheizten Mixer (EMI).
8. **Ars Elemental.** Gelber Archwood-Setzling aus den vier Ars-Setzlingen; Element-Foki in EMI.

Melden: alles, was im Server-Lauf nicht zu sehen ist (fehlende Texturen, leere Tooltips, Bildschirme mit Lücken,
Chat-Zeilen beim Beitreten).

## 0.10.0 Nachtrag (1.0 Runde A 2026-10-15, Pack 0.10.0, firmages-core 0.10.0)

**Voller Reset:** neue Welt und neu importiertes Profil (Client und Server brauchen firmages-core 0.10.0). Alte Welten
verlieren Blöcke, Items und Strukturen der entfernten Mods, und die neuen Welt-Features (Cold Forge, Draconium-Adern,
Vulkan-Minerale, dichtere Ruinen) entstehen nur in neuen Chunks. Server-Teil auf einer Kopie des Test-Servers geprüft
(`dev/poc-results.md`, Abschnitt „1.0 round A (2026-10-15)“): jeder Boot mit neuer Welt, alle Ages per Simulation,
`poc_analyze.py` 91 PASS, `check_log.py` 0 übrig, `selftest all` 55 von 55.

1. **Was fehlt.** Kein Twilight Forest (kein Portal, keine Twilight-Hölzer), keine Bosse aus Cataclysm, Bosses of
   Mass Destruction und Mowzie's Mobs, keine Sigille, Trials und Gateways, kein Monstrosity-Altar, kein TFC Tumbleweed
   (stürzt jeden Server ab). In The Origin gibt es noch keinen Endboss (kommt mit Runde C). In EMI taucht keines davon
   auf, auch kein Vanilla-Bambus, keine Vanilla-Gold- und Eisenblöcke, kein Kettenhemd und kein TNT. Vanilla-Holz
   (Eiche, Birke, Fichte, Tropenholz) und seine Deko erst in der Singularitätszeit (Waldwelten hinter dem Stargate),
   Schwarzeiche, Akazie, Mangrove und Kirsche gar nicht; Wasser- und Lavaeimer aus Vanilla erst in der Elektrikzeit.
2. **Age-Token-Wege** (je einmal in Kreativ oder per `/stage grant` bis zur Age, dann im Überleben bauen):
   - Eisenzeit: Frontier Compass (Schmiedeeisen um Magnetit) zeigt Richtung und Entfernung zur nächsten Cold Forge
     (Hochland, Hügel); in der Kiste die Frontier Shard, Deployer und Presse machen das Frontier Relic.
   - Arkanzeit: Hollow Keystone aus einer Netherfestungs-Kiste (etwa 40 %), Spout mit flüssiger Erfahrung, Deployer
     mit Lohenrute, Presse; der Djinni-Weg mit Wilden Tribute aus dem Enchanting Apparatus. Der Volcanic Sourcelink
     nimmt jetzt einen Magmablock statt eines Lavaeimers.
   - Industriezeit: gebaute Stahl-Kanonenkammer (Create Big Cannons). Elektrikzeit: Nether Star im Lichtbogenofen
     (ein Witherskelett-Schädel). Information: AE2-Singularität. Raumfahrt: Flugschreiber in der Mars-Tempelkiste,
     Drachenherz per Fusion. Quanten: subatomare Legierung. Singularität: großes Chaos-Fragment aus dem eigenen Reaktor.
   - Draconium-Adern in Granit, Diorit, Gabbro (tief) erst ab der Raumfahrt sichtbar; Vanilla-Eimer aus Stahlblechen
     in der Elektrikzeit.
3. **Welt.** Vulkan: Ausbruch und Beben ohne Schaden an Blöcken, an Lava und heißen Quellen wachsen Mineral-Krusten
   (z. B. Salpeter, Schwefel). Monat mit 5 Tagen (Start 1. Juni), Schnee doppelt so schnell. TFC-Ruinen häufiger, ihre
   Truhen in Dawn nur mit Dawn-Kleinkram. Blutmond ab der Eisenzeit: Untote mit Rüstung und Waffe der Vor-Age.
   Cacophony (Vögel, Wind), Particle Rain, Cosy-Critters-Vögel (keine Motten), Subtle Effects ohne Herzschlag.
4. **Exposure.** Kamera aus Messing, Silber, Linse und Leder in der Bronzezeit; Schwarzweißfilm belichten, mit Soda
   und Salz entwickeln, in der Lichtkammer drucken (Licht 13). Kommt das Foto in der Bronzezeit an, ohne dass es aus
   dem Inventar fällt? Farbfilm erst in der Industriezeit; der Interplanar Projector fehlt.
5. **Deko.** Messung und Sichtprüfung laut Abschnitt „n7-Deko“ unten (A = 0.9.3, B = 0.10.0). Zusätzlich: Macaw's
   Couchtisch und Tisch in TFC-Eiche und Storage-Drawers-Upgrades je Age.
6. **Tally Chute** (Bronzezeit, Rezept Bauholz, Create-Rutsche, Bronzeblech): unter einen Trichter oder ein Band
   setzen, Kiste darunter; Jade zeigt die gezählte Menge. Noch keine Quest nutzt sie.
7. **Musik.** Pro Age eigene TFC-Stücke statt der Vanilla-Musik; in der Firmament-Nacht läuft ein eigener Loop, die
   Age-Musik schweigt, ebenso während der Zeremonie. `music.ageMusic = false` in `firmages-client.toml` schaltet zurück.
8. **Ponder** (als OP, W über dem Item halten): „Silo Hatch“ am Ad-Astra-Launch-Pad, „Portcullis“ am Rope Pulley.
   Textposition und Ausrichtung des Launch Pads ansehen.

## n7-Deko (Runde A1 2026-10-15, Zweig a1/deco, für den Integrator)

20 neue Mods (Inventar: `dev/decisions-while-away.md`, Abschnitt „n7-Deko-Inventar“), 126 Gesteinsmuster, Rechiseled.
Kein Server- und kein Client-Start in dieser Runde. Zuerst der Server-Boot mit `fa_dump` (siehe Notiz an den
Integrator), dann diese Messung und die Sichtprüfung.

### Messung: Startzeit und Heap mit dem vollen Set

Ziel nach `research-raw/n7-build.md` §6: höchstens +20 s Start und +300 MB Heap pro Tier. Gemessen wird A (Stand vor
n7, `dev` bei 27cfbf6) gegen B (mit n7), auf demselben Rechner, je drei Läufe.

1. **Vorbereitung.** Prism-Dev-Instanz (`tools/PrismLauncher`, `dev/archive/dev-client.md`) oder ein eigenes
   CurseForge-Profil, nie Felix' Spielprofil. RAM-Einstellung der Instanz notieren und für A und B gleich lassen
   (`-Xmx` wie bisher, kein Ändern zwischen den Läufen). Shader wie im Auslieferungszustand (Complementary an), andere
   große Programme zu.
2. **Stand setzen.** `tools\packwiz.exe serve` im jeweiligen Stand (A: Worktree auf 27cfbf6, B: Zweig `a1/deco` bzw.
   der Integrationsstand), Instanz einmal starten und schließen, damit packwiz-installer synchronisiert hat und Every
   Compat seine Ressourcen einmal gebaut hat. Dieser erste Start zählt nicht.
3. **Drei Läufe je Stand.** Starten, im Titelbild 10 s warten, in eine feste Testwelt (Einzelspieler, dieselbe Welt
   für A und B, mit einem eingerichteten Haus aus Deko-Blöcken) einsteigen, 60 s stehen bleiben, dann
   `/sparkc heapsummary` und `/sparkc gc` und erneut 30 s warten, Spiel schließen. Nach jedem Lauf
   `logs/latest.log` als `n7-<A|B>-<n>.log` wegkopieren.
4. **Auswerten je Log.** `python dev/check_log.py --client n7-B-1.log` gibt die Startphasen aus: „total up to the title
   screen“ (Startzeit), „first resource reload up to the title screen“ (Modelle und Atlas), pro Einstieg „in the world
   after …“ und „EMI … s“. Zusätzlich aus dem Log:
   - `Created: <B>x<H>x4 minecraft:textures/atlas/blocks.png-atlas` (bleibt der Blockatlas bei 8192x8192?)
   - `Generated runtime CLIENT_RESOURCES for pack everycomp:dynamic_resources in N s` und `Registered N compat blocks`
     (Every Compat; erwartet etwa 1.670 + 2.392)
   - die AllTheLeaks-Zeile `B: … MB / C: … MB` nach dem Einstieg (Heap) und die Summe aus `heapsummary`
5. **Ergebnis.** Mittelwert und Spanne von A und B je Größe in `dev/poc-results.md` (neuer Abschnitt „n7 deco
   measurement“), Differenz B − A gegen das Budget. Über Budget: Every-Compat-Sätze in dieser Reihenfolge kürzen
   (`config/everycomp-hazardous.toml`): Macaw's Furniture, dann Roofs, dann Another Furniture; danach Rechiseled
   prüfen (3.627 versteckte, aber registrierte Blöcke).
6. **Bildrate.** In einem Raum voller Möbel mit Iris an: `/sparkc profiler start`, 60 s umsehen,
   `/sparkc profiler stop`; Link und mittlere Bildzeit in denselben Abschnitt.

### Sichtprüfung (nach der Messung, Stand B)

1. **TFC-Meißel.** Granitziegel setzen, mit dem Kupfermeißel im Glätten-Modus zehnmal bearbeiten: Gemeißelt, Engraved,
   Chevron, Ashlar, Runed, Mosaic, Paneled, Fluted, wieder Gemeißelt.
2. **Verbundtexturen.** Eine 3x3-Wand „Paneled Granite“: ein Rahmen außen herum, keine Fugen innen; eine Lücke in der
   Wand bekommt einen eigenen Rahmen. Drei „Fluted Granite“ übereinander: oben Kapitell, unten Basis, dazwischen
   durchgehende Kanneluren. Fehlt die Verbindung, lädt das KubeJS-Modell mit `loader: fusion:model` nicht (Log nach
   `fusion` durchsuchen).
3. **Rechiseled.** Bronzemeißel bauen, Granitziegel hineinlegen: genau die Granit-Familie (Ziegel, Gemeißelt, Engraved,
   sechs Muster). Ein Vanilla-Stein zeigt nichts.
4. **EMI.** Keine Vanilla-Holz-Variante von Macaw's, Storage Drawers, Another Furniture oder Dusty Decorations
   sichtbar, keine Rechiseled-Blöcke; TFC-Eiche als Küchenschrank, Schublade (Drawers) und Fensterladen
   (Another Furniture) mit TFC-Rezept.
5. **Rezepte.** Kitchen Sink (Macaw's, TFC-Holz) nimmt einen TFC-Holzeimer; Lampen von Another Furniture eine
   TFC-Fackel; Drawer-Upgrades Kupferblech, Schmiedeeisenblech, Goldblech.
6. **Hecken.** Gibt es `everycomp:mcf/tfc/oak_hedge`? Wenn ja, erkennt Moonlight TFC-Blätter; dann die Hecken-IDs
   in den Registry-Snapshot (macht der Dump ohnehin).

## 0.9.3 Nachtrag (Runde N6 2026-10-14, Pack 0.9.3, firmages-core 0.9.2)

Nur Texte, Quests, Stage-Configs und ein Skript; firmages-core bleibt 0.9.2. Server-Teil geprüft auf einer Kopie des
Test-Servers mit neuer Welt (`dev/poc-results.md`, Abschnitt „0.9.3 round N6“): Log ohne offene Zeile, `selftest all`
55 von 55, 351 Quests geladen, alle Dump-Prüfungen grün. Test-Server und Client-Profil auf 0.9.3 bringen.

1. **Kupferweg.** Steinzeit: „Small Vessel“ sagt, dass Erz im kleinen Gefäß im Grubenofen schmilzt (nie im
   Feuerplatz); „Copper Ingot“ erklärt das Gießen (glühendes Gefäß rechtsklicken, Form in den Slot, abgekühlte Form
   rechtsklicken). Einmal nach dem Text durchspielen: Kommst Du nur mit den Quests zum Kupferbarren?
2. **Geräte.** „Potter“ (Grubenofen, V setzt die Teile), „A Better Fuel“ (Kohlenmeiler), „Forging“
   (Holzkohleschmiede) und die neue Notiz „Anvil Work“ neben „Working Heat“: Stimmen die Schritte im Spiel?
3. **Welcome to the Wild** nennt Strg als Schleichtaste und Shift als Sprint.
4. **Ring-Quests.** Bronze- und Eisenzeit haben je eine Raute „Raise the Bronze Sanctum“ / „Raise the Iron Sanctum“;
   kein Ring-Untertitel sagt mehr „optional“.
5. **Firmament-Kapitel.** Am Anfang nur „The First Spark“; jede Opfergabe erscheint, wenn ihre Age anbricht (mit
   `/stage grant @s age_N` nacheinander prüfen), „Beyond the Firmament“ zusammen mit „Quantum Core“ (`age_8`). „A Weird Rock“ ist weg.
6. **Singularitätszeit.** „Into The Origin“ liegt als Pflichtschritt zwischen Tor-Keystone und Ziel: Sehen die
   Verbindungslinien sauber aus? „Antimatter Pellets“ fordert 2.
7. **Meldungen.** Fällt ein Item einer späteren Age aus dem Inventar: „What is not of this Age falls from your pack.“
   ohne Zahl. Beim Gathering in The Origin kommt nur eine Zeile („… Something wakes in the dark.“), nicht zwei.
8. **Stage-Namen** (als OP): „Opens with …“-Tooltips, `/stage list` und `/stage info` zeigen „Stone Age“, „Nights of
   the Bronze Age“, „Storage Terminal“ usw., ohne Nummern und Modnamen.

## 0.9.2 Nachtrag (Runde N5 2026-10-14, Pack 0.9.2, firmages-core 0.9.2)

Drei Arbeitspakete (Client-Log, Mods und Ladebildschirm, firmages-core) plus die Review-Korrekturen aus 0.9.1.
Server-Teil geprüft auf einer Kopie des Test-Servers mit neuer Welt (`dev/poc-results.md`, Abschnitt „0.9.2 round N5“):
Log ohne offene Zeile, `selftest all` 55 von 55, 49 GameTests, alle Dump-Prüfungen grün. **Neues Profil importieren**
(Zip aus `dev/release.md`, Abschnitt 1) und den Test-Server auf 0.9.2 synchronisieren. Netzwerkprotokoll 4 bleibt, aber
die neuen EMI-Kategorien und der Knopf-Filter stecken im Client-Jar.

1. **Ladebildschirm.** Unten rechts im späten Ladebildschirm steht „Memory … MB of … MB“ (klein, in der Farbe der
   Versionszeile), der Balken läuft mit dem echten Fortschritt und ist voll, wenn das Titelbild kommt. Das frühe
   Fenster hat keine Speicherzeile.
2. **Kein Zahnrad.** Neben dem Online-Knopf im Titelbild und im Pausemenü fehlt der kleine Zahnrad-Knopf von
   Supplementaries („s“). Doppelt abgesichert: `configs_button = false` in `supplementaries-client.toml` und der Filter
   `ui.hiddenMenuWidgets` in `firmages-client.toml`.
3. **Keine Shader-Wundertüten.** Bosse, der Bulwark und Fortschritte geben keine Immersive-Engineering-Shader-Tüten
   mehr; Tüten und Shader fehlen in EMI.
4. **Mod-Updates.** ImmediatelyFast 1.6.15 und Sophisticated Core 1.5.12. In der CurseForge-App Supplementaries nicht
   auf 4.x aktualisieren (gepinnt auf 3.9.9) und Create: Applied Kinetics auf 1.5.4 lassen, auch wenn die App
   „incompatible“ meldet.
5. **The Origin: ein Gathering pro Kampf.** Während Wellen oder der Endboss leben, startet ein Zurückkommen zum Altar
   nichts mehr; „The altar answers …“ kommt einmal pro Kampf. Zum schnellen Durchspielen als OP:
   `/firmages origin gathering`, dann `/firmages origin kill` je Welle (die Welle zählt als geschafft), dann
   `/firmages origin kill boss` (Sieg wie im echten Kampf). `/firmages origin status` zeigt, ob der Kampf läuft.
6. **Wegweiser.** Supplementaries-Wegweiser aus TFC- und AFC-Holz haben eigene Icons (Pfosten mit zwei Brettern);
   keiner zeigt mehr die lila-schwarze Fehltextur. Die Design-n-Decor-Text-Plate zerbricht mit eigenen Partikeln.
7. **Configs.** Beim ersten Start schreibt NeoForge keine „is not correct. Correcting“-Zeile und keine `.toml.bak` mehr
   für die zehn ausgelieferten Configs. Prüfen: `python dev/check_log.py --client <Instanz>/logs/latest.log` endet mit
   Exit 0; die Zeile `EMI audit …` steht als INFO da (WARN nur noch bei einem echten Leck).
8. **EMI: Evolved Mekanism.** Ab der Quantenzeit zeigt EMI zwei neue Kategorien, „Chemixer“ und „Antimatter
   Protomolecular Transmutator“, mit den sechs Rezepten für Plaslitherit und Better Gold (Chemikalien als Tanks).
9. **Steinzeit (Review 0.9.1).** Die Quest „Workbench“ steht vor „The Shrine Heart“: Sägeblatt-Form (in „Clay
   Patterns“), Säge, Bretter, Werkbank; Schrein-Herz, Opfersockel und Hearthstone zeigen ihr Rezeptgitter. „A Home“
   nennt jedes Heimrezept der Steinzeit; Framed Blocks, Vanilla-Holz von Decorative Blocks und Macaws Steinbrücken sind
   Bronzezeit, Wandleuchter Eisenzeit. Classic- und Cottage-Türdrucke nehmen nur noch TFC- und AFC-Türen
   (Vanilla-Türen nicht mehr).

## 0.9.1 Nachtrag (Runde N4 2026-10-14, Pack 0.9.1, firmages-core 0.9.1)

Vier Arbeitspakete: Team-Übernahme und EMI für Operatoren (firmages-core), Texte und Questbuch, Deko in TFC-Hölzern,
Log-Warnungen. Server-Teil geprüft auf einer Kopie des Test-Servers mit neuer Welt (`dev/poc-results.md`, Abschnitt
„0.9.1 polish round N4“): Log ohne offene Zeile, `selftest all` 55 von 55, alle Dump-Prüfungen grün. **Neues Profil
importieren** (Zip aus `dev/release.md`, Abschnitt 1) und den Test-Server auf 0.9.1 synchronisieren: Die Lang-Texte und
die EMI-Änderungen stecken im neuen firmages-core-Jar. Netzwerkprotokoll 4 ist unverändert.

1. **Zweiter Spieler auf Deiner Welt.** Deine age_9-Welt nimmt jetzt einen zweiten Spieler auf. Beim nächsten Login
   gründet HiighHopes die Party „Firmament“, und die Party bekommt sofort alle Ages der Welt: age_0 bis age_9 und, falls
   schon errungen, finale_won. Dein Questbuch geht mit, es gibt keine Zeremonie und keine Firmament Night. Prüfen:
   einloggen, `/stage list` zeigt weiter alle Ages, Rezepte und Items bleiben offen. Dann den zweiten Spieler
   einloggen: Er landet ohne Einladung in der Party („You join Firmament. Its Ages are yours.“) und hat alle Ages.
   Loggt er sich ein, während Du offline bist, liest er „HiighHopes holds this world's Ages. The fellowship gathers
   when they return.“; sobald Du kommst, entsteht die Party und er ist mit drin. Nur Dein Questfortschritt geht in die
   Party, nicht der eines zweiten Spielers (Regel von FTB Quests).
2. **EMI für Operatoren.** Als Operator oder im Kreativmodus hast Du EMI mit Rezeptfenster auch in Dawn und
   Steinzeit; Überlebens-Spieler ohne OP sehen es weiter erst ab der Bronzezeit. Prüfen: OP in Dawn, EMI öffnet; OP
   entziehen und Überlebensmodus, EMI geht wieder zu. Der kurze Hänger (etwa 1,5 bis 2 s) bei jedem Age-Wechsel bleibt
   bewusst: JEI lädt neu, weil EMI die Rezepte von 52 Mods nur über JEI bekommt.
3. **EMI zeigt in Item-Listen nur freigeschaltete Items.** Ein Rezeptfeld, das mehrere Items durchwechselt, zeigt nur
   noch Items Deiner Ages. Prüfen ab der Bronzezeit: `tfc:crafting/blue_dye` wechselt nicht mehr zum Lapis-Pulver,
   der Eisennugget aus dem Schmelzen zeigt keine gesperrten Vanilla-Werkzeuge. Ein Rezept, dessen Liste nur gesperrte
   Items hat, bleibt verborgen wie bisher.
4. **Questbuch.** Die Kapitel heißen nur noch nach ihrer Age („Stone Age“ bis „Singularity Age“), auch auf den
   Bannern. Das Buch heißt überall „the Chronicle“, Bedienung steht als „right-click“ da. Prüfen: Banner und
   Kapitelliste sehen sauber aus; das Welcome-Kapitel hat vier Quests; der Willkommensbildschirm zeigt drei kurze
   Sätze; Jade am Schrein-Herz zeigt „Ages: N of 9“. Im Firmament-Kapitel zeigt das letzte Ziel einen Netherstern
   statt des Stargates und nennt The Origin nicht. In der Steinzeit nennt „Locating Clay“ die sechs Zeigerpflanzen,
   „Copper Ingot“ sagt „Ten small pieces make one ingot“, das Steinzeit-Ziel erwähnt den Strahl als Weg nach Hause.
   Space-, Quantum- und Singularity-Stränge haben je zwei kurze Sätze, Data Matrix nennt drei Advanced Control
   Circuits. Mit zwei Spielern eine Nebenquest-Belohnung abholen: Jeder wählt seine eigene; die Keystone-Belohnung holt
   weiter nur einer fürs Team.
5. **Deko in TFC-Hölzern ab der Bronzezeit.** In EMI: „Classic Door Print“ aus Papier und einer TFC- oder AFC-Holztür,
   „Cottage Door Print“ aus Papier und einer TFC- oder AFC-Holzfalltür. Eine classic- und eine cottage-Tür aus TFC-Holz
   bauen (z. B. Eiche). Eine Handcrafted-Theke aus TFC-Holz mit glattem Gestein statt Calcit herstellen (z. B. glatter
   Granit, Meißel).
6. **Ruhigeres Server-Log.** Nach dem Sync und Neustart: `python dev/check_log.py test-server/logs/latest.log` endet
   mit Exit 0 (auf der Kopie: 0 übrig). Seife plus gefärbte TFC-Kerze oder gefärbter Alabasterziegel ergibt wieder die
   ungefärbte Variante. Im Occultism-Buch fehlen die Einträge zu Bienennest, Kelchen, Transport-Foliot, Wiederbelebung
   und schwacher Breeze; „Possess Breeze“ steht unter der Übersicht. Beim ersten Login nach dem Start auf den Ruckler
   von etwa 6 s achten („Can't keep up“ im Log); der ist noch offen.

## 0.9.0 Nachtrag (Nachtlauf N3 2026-10-14, Pack 0.9.0, firmages-core 0.9.0)

Sechs Arbeitspakete in einer Version: Age-Wechsel ohne Datapack-Reload, EMI/Ponder/GuideME nach Age, Deko-Mods,
Schrein und Origin, Firmament Night, neue Texte. Server-Teil geprüft (`dev/poc-results.md`, Abschnitt „0.9.0 night run
N3“: Boots auf Deiner age_9-Welt, Log ohne offene Fehler, Registry-Vergleich ohne entfernte Id), im Spiel noch offen.
**Neues Profil importieren** (Zip aus `dev/release.md`, Abschnitt 1): Client und Server brauchen beide firmages-core
0.9.0 (Netzwerkprotokoll 4), 216 Mods. Der Test-Server ist auf 0.9.0 synchronisiert, `permanentBeam = true` steht schon
in `test-server/config/firmages-server.toml`. Das Titelbild zeigt Shader-Fehler weiter im Menü
(`show_compile_errors = true`), bis Du das Sternbild bestätigt hast; danach kommt der Schalter auf `false`.

**Deine Welt:** Vom Schrein steht laut `/firmages shrine status` in jedem Ring nur noch ein Block (der Hearth Circle hat
1 von 22 Blöcken), keine Reliquie liegt auf einem Sockel. Strahl, Summen, Staub und das Gathering (Punkte 4 und 6)
lassen sich erst nach dem Wiederaufbau prüfen; das Gebet antwortet jetzt mit „Caelum does not dwell in ruins“ und
Funken an den Lücken.

1. **Erster Start, EMI nach Age.** In Dawn und Steinzeit bleibt EMI aus; auch über Klickflächen anderer Fenster öffnet
   sich kein EMI-Rezeptfenster. Ab der Bronzezeit zeigt EMI nichts aus späteren Ages: keine Flüssigkeiten späterer Mods
   (Raketentreibstoff, Mekanism- und IE-Flüssigkeiten), keine Mekanism-Chemikalien, keine Bücher, Verzauberungen oder
   Tränke späterer Mods, keine Rezepte mit gesperrter Zutat, keine Kategorien wie „Blast Furnace Fuel“, Boiler oder SPS,
   keine EMI-Loot-Seiten späterer Mods. Prüfen: Nach jedem `/stage grant` steht im Client-Log
   `EMI audit <age>: ... locked stacks visible 0, recipes with a locked stack 0, locked workstations 0, locked categories 0`
   und genau ein `[EMI] Starting Reload` (nicht zwei). `/firmages_client emi_audit` schreibt die Details nach
   `logs/firmages-emi-audit-<age>.json`. `/ponder` antwortet Nicht-OPs nur grau „Hold [W] on an item to ponder it.“,
   W auf einem Item öffnet die Szenen weiter, der Ponder-Index (Create-Konfigmenü) zeigt nur freigeschaltete Items,
   `/guidemec` ist ein unbekannter Befehl. Erwartet: Die Zahl sichtbarer Rezepte pro Age ist deutlich kleiner als in 0.8.1.
2. **Deko-Mods (Stone und Bronze Age).** Steinzeit: Palisaden, Balken, Stützen, Sitzbänke (Decorative Blocks), Seil-,
   Balken- und Geländerbrücken mit Pfeilern (Macaw's Bridges) in allen TFC- und AFC-Hölzern, Fachwerk, Lehmputz,
   Jute-Seil, Wandleuchter (brennen aus wie TFC-Fackeln), die Stone Age Lantern (Kupferlampe plus Holzkohle) und
   gravierte Steinziegel (TFC-Meißel, Modus glätten, auf gemeißeltem Gestein; 21 Gesteine). Bronzezeit: Möbel
   (Handcrafted), Fenster, Läden, Jalousien, Vorhangstangen (Macaw's Windows), Türen in acht Stilen (Macaw's Doors),
   Schilderpfähle, Notizbretter, Gläser, Säcke, Flaggen, Sockel (Supplementaries). Prunkbetten ab der Eisenzeit, jede
   Age hat eine eigene Laterne. Prüfen: alle Stile in einem TFC- und einem AFC-Holz platzierbar; der Wandleuchter brennt
   aus; ein Sack macht schwer; Prunkbett vor der Eisenzeit nicht herstellbar; EMI zeigt weder Bomben, Kanonen noch
   Tresore. Abweichungen aus dem Lauf: Die „classic“-Türen und die Handcrafted-Theken kommen erst ab Eisen- bzw.
   Arkanzeit (sie verlangen einen Vanilla-Holzdruck bzw. Calcit), siehe `dev/offene-punkte.md`. **Boot- und
   Heap-Budget** (höchstens +20 s bis zum Titel, +300 MB Heap) nach `dev/archive/dev-client.md`, Abschnitt „Boot- und
   Heap-Budget der Deko-Mods messen“; Ergebnis in `dev/poc-results.md` eintragen.
3. **Age-Wechsel ohne Ruckler.** Ein neues Age lädt keine Datapacks mehr. Auf Deiner Welt dauerte der Swap 0,3 bis 0,7 s
   (vorher 9 bis 15 s Freeze). Prüfen: beim Freischalten kein Einfrieren; Server-Log „Age swap (granted age_x) finished
   in N ms“; Maschinen übernehmen neue Rezepte sofort ohne Relog (Create-Mischer, beheizt erst mit Heizer, IE-Brecher,
   Mekanism-Anreicherungskammer, TFC-Fass, ein Occultism-Ritual); JEI startet kurz neu; der Prospektorpick findet ein
   Erz erst ab seinem Age; `/reload` wie bisher. Notbremse: `gate.ageSwap = false` in `config/firmages-server.toml`.
   Nach dem Review zusätzlich, jeweils direkt nach dem Swap ohne Neustart: die **Werkbank** stellt ein Rezept der neuen
   Age her (FastSuite-Cache) und ein entzogenes nicht mehr; nach Dawn zur Steinzeit die **Framing Saw** (FramedBlocks);
   ab der Arkanen Zeit die **Imbuement Chamber** (Source Gem aus Amethyst); ab der Elektrozeit **Destillationsturm,
   Coker und Hochdruck-Raffinerie** (Immersive Petroleum); ab der Informationszeit die **AE2-Umwandlung im Wasser**
   (Fluix-Kristall aus Ladekristall, Redstone und Quarz). Server-Gegenprobe: `/firmages selftest server`, Fall
   `recipe_caches_fresh`, und `/firmages recipes craft <Rezept-Id>`.
11. **Review-Nachträge im Spiel.** Dawn: Nebenquest „Before Dark“ (Feuerstelle, Fackeln) über dem Stein-Strang, der
    Willkommensbildschirm sagt „Nights are dark: light a fire before the sun sets.“. Steinzeit: „Unfired Hearth Idol“
    zeigt das Muster und verlangt zwei Idole, die Notizen „Clay Patterns“ und „A Home“ unten; bitte ein Idol nach dem
    Muster knappen und im Grubenofen brennen. Bronze- und Eisenzeit je eine Notiz „A Home“; „The Way Below“ beschreibt
    das Opfer. Team: Auf einer neuen Welt gründet der erste Spieler die Party „Firmament“, jeder weitere landet beim ersten Login
    ohne Einladung darin (Chatzeile „You join Firmament. Its Ages are yours.“). Auf Deiner Welt seit 0.9.1: siehe „0.9.1 Nachtrag“, Punkt 1
    (die Party übernimmt die Ages der Welt). The Origin: Steht nur ein Teil am Altar, zeigt die Aktionsleiste „The altar waits for N more: ...“;
    wer 5 Minuten nichts tut, zählt nicht.
4. **Lebendiger Schrein.** Am entzündeten, heilen Schrein steigt Tag und Nacht ein dünner Strahl in der Farbe der
   zuletzt erweckten Age auf. Prüfen: aus etwa 200 Blöcken sichtbar, mit und ohne Complementary; einen Ringblock in der
   Wartung abbauen, der Strahl erlischt innerhalb einer Sekunde, nach der Reparatur ist er wieder da. Nach dem Endboss
   ist er golden und etwas breiter. Innerhalb von 16 Blöcken summt das Herz leise (alle 4 s, mit jeder Age etwas
   höher), weiter weg nicht. Jede Reliquie auf ihrem Sockel gibt ab und zu Staub in ihrer Age-Farbe ab. FPS mit Strahl
   gegen ohne vergleichen. Abschalten pro Spieler: `firmages-client.toml` `[effects] idleBeam = false` bzw.
   `ambientDrone = false`.
5. **Echo der Zeremonie.** Mit zwei Spielern: Spieler B loggt aus, A öffnet eine Age (Gebet oder
   `/stage grant A age_N`). B loggt wieder ein: etwa 5 s nach dem Laden kommt einmal die kurze Zeremonie der neuen Age
   (Titel, Caelums Satz) und eine Chatzeile „While you were away: Bronze Age.“ (bei mehreren verpassten Ages alle
   Namen). Prüfen: Der Titel steht nach dem Ladebildschirm, nicht darunter; ein zweites Einloggen spielt nichts mehr; das
   Finale wird nie wiederholt.
6. **Das Gathering (age_9).** In der Singularitätszeit mit allen neun Reliquien auf ihren Sockeln am heilen Schrein
   beten (schleichen, Rechtsklick halten; zu zweit, sobald zwei online sind). Rechtsklick auf das Herz vorher sagt
   „Pray together: Caelum gives the nine relics back for the last forging.“ Danach: Titel „The Gathering“, Caelum
   „Nine gifts. One light. Open the gate.“, Zeile „Crowned“, die neun Reliquien fallen als Items über dem Herz heraus
   (verschwinden nie, brennen nicht), die Sockel leuchten weiter und warten. Die HUD-Zeile oben links zeigt dann
   „Goal: Beyond the Firmament“. Prüfen: Fehlt eine Reliquie, nennt der Schrein ihre Age; eine zurückgelegte Reliquie
   lässt sich mit Schleichen und leerer Hand wieder nehmen. Die Ultimate Singularity mit den neun Items in der
   DE-Fusion herstellen (GUI).
7. **The Origin: Inseln.** Die neun schwebenden Inseln sind jetzt aus geweihtem Schrein-Marmor mit Rand in ihrer
   Age-Farbe, und über jeder dreht sich langsam das Zeichen-Item ihrer Age (Hearthstone, Sky Disc, ... Quantum Core),
   voll hell. Prüfen: Alle neun sichtbar von der Arena aus, kein Item doppelt, die Drehung ruckelt nicht.
8. **Befehle für Deinen Test (nur Operatoren, Stufe 2).**
   - `/firmages origin tp <Spieler> force`: in die Origin ohne age_9 und ohne Kreativmodus; beide Sperren lassen Dich
     bleiben, bis Du die Origin verlässt, dort stirbst oder der Server neu startet.
   - `/firmages origin gathering`: startet den Altar-Auslöser sofort, egal wo ihr steht und ohne Wartezeit (die
     Gateway-Wellen kommen dann wie im echten Kampf).
   - `/firmages origin fight`: ruft The Primordial sofort, ohne Wellen. Verweigert, solange das Finale-Gate oder der Boss
     lebt, und nach dem Sieg (dann vorher `/firmages origin reset`).
   - `/firmages origin kill` (N5): tötet alle Feinde in der Origin außer dem Endboss, also die Gegner der laufenden
     Gate-Welle (Maledictus, Ignis) und die Helfer der Boss-Phasen (Ender Golems, Endermapteras). Die Welle zählt als
     geschafft, das Gate startet die nächste; nach der letzten erscheint The Primordial.
   - `/firmages origin kill boss` (N5): tötet The Primordial. Der Sieg läuft wie im echten Kampf (finale_won, Finale-Zeremonie,
     Ansage „The Primordial falls ...“).
   Schnell durchspielen: `gathering`, dann `kill` je Welle (oder gleich `fight`), dann `kill boss`.
   Prüfen: ohne Operator-Rechte gibt es keinen der fünf Befehle; nach `fight` laufen Phasen, Ansagen und das Finale wie
   im echten Kampf.
9. **Firmament Night.** In der ersten Nacht nach jedem neuen Age leuchten Himmel und Mond in der Farbe dieses Ages, der
   Mond ist größer, zur Dämmerung steht eine Zeile über der Hotbar („Tonight the sky turns to bronze.“), halb so viele
   Monster, 1,5-fache Erfahrung. Kommt das Age nach Mitternacht, gehört die nächste Nacht dem Himmel; Regen wird bis
   zum Morgen weggeschoben. Jede Nacht einmal pro Welt, nie zufällig; Deine Welt ist schon bei age_9 und bekommt keine
   Nachhol-Nacht. Prüfen auf einer frischen Welt: `/stage grant <Spieler> age_0` am Tag, nach der Dämmerung
   `/ec lunarForecast`, Färbung mit und ohne Shader, die Hotbar-Zeile nur einmal, keine Chatmeldung. Die vier Relikte
   Hearthstone, Sky Disc, Steel Heart und Pressure Core bewegen sich jetzt. Beim Import des CurseForge-Zips notieren, ob
   die App 8 GB RAM vorschlägt.
10. **Questbuch und Texte.** Jede Quest sagt zuerst, was zu tun ist; keine Ausrufezeichen und Modnamen in Titeln
    („Stone Tools“, „Blast Furnace“, „Barrel“, „A Jug of Water“ jetzt vor dem Topf); Eingänge „The X Age Dawns“; in
    den Zielen die Riten (Tesla-Crown-Kondensatoren mit 1.000.000 FE, Awakened Keystone zurück auf den Sockel); eine
    Notiz „When a Boss Will Not Come“ in der Eisenzeit; die Singularitäts-Quest heißt „The Gathering“ (erfüllt erst bei
    `awakened=10`). Prüfen: gesperrte Gegenstände zeigen grau „Something Unknown“ und „Not of this Age“ ohne rotes
    Schloss; beim Age-Wechsel keine „Age N: …“-Zeile im Chat; der Willkommensbildschirm sagt „Open the Chronicle (K)“;
    Stein mit der Hand in Dawn: „Stone needs a pickaxe. The first one is cast in the Stone Age.“; das Arkan-Ziel passt
    bei GUI-Skala 3 ohne Scrollen ins Questfenster.

## 0.8.1 Nachtrag (Nachtlauf 2026-10-14, Pack 0.8.1, firmages-core 0.8.1)

Dein Feedback vom 14.10. (Hand-Abbau Option A, Titelbild Option A, Tooltip „Light / Small“, Knapping-Erklärung) plus
Log-Aufräumen, schnellerer Start und Create in der Bronzezeit. Server-Teil geprüft (`dev/poc-results.md`, Abschnitt
„0.8.1 night run“: drei Boots auf Deiner age_9-Welt, Log ohne offene Fehler, Registry-Vergleich gleich), im Spiel noch
offen. **Neues Profil importieren** (Zip aus `dev/release.md`, Abschnitt 1): Client und Server brauchen beide
firmages-core 0.8.1 (Netzwerkprotokoll 3), Sichtfeld 90 und der abgeschaltete Field-Guide-Kasten kommen nur in ein
frisches Profil. Der Test-Server ist schon auf 0.8.1 synchronisiert und hat `inferredMetals` in `config/tfc-server.toml`.

1. **Titelbild (Shader).** Der Titelbildschirm zeigt den Schrein als 3D-Sternbild. Die Kamera kreist ohne Maus von
   selbst (ein Umlauf in 90 s), alle 120 s leuchten die neun Ringe nacheinander in ihrer Age-Farbe auf, halten und
   verblassen. Keine Diashow, kein Text im Bild; Wortmarke, die vier Knöpfe und die Versionszeile („Firmament Ages
   0.8.1“) wie bisher. Beim ersten Start kurz zuschauen: Der Hintergrund dreht sich ruhig, die Maus bewegt ihn nicht.
   Steht statt des Sternbilds ein Fehlertext im Menü (`show_compile_errors` ist für diesen Test an), den Text bitte
   wörtlich oder als Screenshot schicken. Ruckelt das Menü, die FPS im Titelmenü melden.
2. **Schnellerer Start.** Der Server lädt auf Deiner Welt in 54 s statt 75 s (gleicher Build mit dem Schalter aus),
   Deine letzte 0.8.0-Zeile war 117,6 s. Bitte am Client messen: Startet er spürbar schneller (erwartet etwa 20 s)?
   Fehlen die TFC-Wagen für eine Holzart in EMI (auch AFC-Hölzer wie Koa: Handkarren, Pflug, Sämaschine, Mäher,
   Vorratswagen, Rad)? Auf dem Server sind alle 567 da. Falls etwas fehlt: `firmages-server.toml` `[perf]`
   `limitTfcRegistryApiScan = false`, neu starten.
3. **Hand-Abbau in Dawn (Option A).** Neue Welt oder Dawn-Test: Ein Holzstamm, roher oder gehärteter Stein, Erz oder
   Sandstein bricht ohne passendes Werkzeug gar nicht: kein Riss, kein Fortschritt. In der Aktionsleiste steht einmal
   „You need an axe for this.“ (bei Stein und Erz „You need a pickaxe for this.“), danach höchstens einmal pro Minute.
   Mit der geknappten Steinaxt (oder dem Steinhammer) fällst Du den Baum normal. Erde, Kies, Sand, Laub, Zweige und
   lose Steine gehen weiter mit der Hand. Nach `/stage grant @s age_0` gilt die Regel nicht mehr (Hand wieder
   TFC-langsam, ohne Drop). Abschalten: `firmages-server.toml` `[hardship] toolRuleUntilAge = "never"`, Dawn und
   Steinzeit (Option B): `"age_1"`. Bekanntes Risiko: Wer in eine Grube aus rohem Stein fällt, kommt bis zur Steinzeit
   nur mit Erde oder Kies zum Hochbauen raus.
4. **Knapping im Questbuch.** In Dawn das Questbuch öffnen: „Just a Rock“, „A Bundle of Sticks“ und „Paleolithic!“
   erklären Schritt für Schritt: Steine aufheben, mindestens zwei in die Hand, Rechtsklick in die Luft, dann im
   5x5-Raster Felder wegklicken (der erste Klick verbraucht einen Stein). Das Axt-Muster steht als Raster aus X
   (stehen lassen) und - (wegschlagen), dann Axtkopf und Stock am Boden (Quest „By Hand“, siehe Punkt „Dawn ohne
   2x2-Feld“). Unten im Kapitel steht die neue
   Erklärquest „Knapping Patterns“ mit Hammer, Schaufel, Hacke und Speer. Prüfen: Stehen die Raster als saubere Spalten
   untereinander (GUI-Skala 3)? Ergibt das Axt-Raster, genau nachgeklickt, einen Axtkopf, und das Messer-Raster zwei
   Klingen aus einem Stein?
5. **Tooltips.** Die TFC-Zeile mit Waagen- und Pfeilsymbol („Light / Small“) steht auf keinem Item mehr. Ein gesperrtes
   Item zeigt nur noch „Unknown Item“ und die ProgressiveStages-Zeilen (kein „Melts into ...“). Der Field-Guide-Kasten
   („?“ und „Ctrl“) ist weg, Strg öffnet kein Buch mehr; im alten Profil dafür `config/tfc-client.toml`
   `showGuideBookLinksAlways = false` von Hand setzen.
6. **Mana-Leiste.** Die lila Mana-Leiste von Ars Nouveau unten links fehlt bis zur Arkanen Zeit und erscheint ab
   `age_3` ohne Relog.
7. **Create ohne Eisen in der Bronzezeit.** Mit einem Bronzezeit-Stand in EMI prüfen, dass diese Rezepte mit Bronze
   erscheinen und herstellbar sind: Erntemaschine, Pflug, Lorenkupplung, Filter, Item-Luke, Item-Tresor, Metallhalter,
   Superkleber, Sticker und klebriger Kolben (TFC-Leim statt Schleim), Tachometer und Stressometer (Bronzeblech statt
   Kompass), Rinne, Seilrolle, Tischglocke, Wurfauswerfer, Kupfer-Ventilgriff und die eingefärbten Ventilgriffe, alle
   16 Werkzeugkästen, Portable Storage/Fluid Interface, Create Connected Freilauf- und Fliehkraftkupplung und
   Item-Silo (Tresor und Silo mit jedem TFC-Fass). Wo Create Eisennuggets wollte, steht ein Bronzestab, statt der
   Eisenfalltür eine Bronzefalltür. EMI zeigt für diese Items kein zweites, gesperrtes Eisenrezept, TFCreate-Bohrkopf
   und -Sägeblatt sind ausgeblendet. Außerdem in der Bronzezeit: Totemic-Schnitzmesser (Bronzebarren), der
   Sophisticated-Upgrade-Rohling (Bronze), die Astikor-Sämaschine (Bronzeblech); in der Steinzeit: Totemfackel
   (TFC-Fackel), Flüssigkeits-Mülleimer (TFC-Holzeimer), FramedBlocks-Framing-Säge (Kupfer).
8. **Maschinen in ihrer echten Age.** TFC-Hochofen, Klingenachsen, Fasspressen, Fässchen, Lorenkisten, Fallenkisten,
   AFC-Baumzapfer, Firmalife-Komposttrommel, Hydrokultur und Ofenteile stehen jetzt in der Eisenzeit, Vat,
   Abfüllstation, Pflücker, Solartrockner und Kehrer in der Industriezeit, so wie man sie tatsächlich bauen kann.
9. **Reparierte Rezepte und Log.** Der Mahlstein macht aus Quarzit mit 5 % Chance Quarz, gefärbte Alabasterziegel
   lassen sich im Mixer mit Lauge bleichen, gefärbte Schilder werden mit weißem Farbstoff wieder weiß, das Metro-Fenster
   (Bells & Whistles) nimmt jedes Glas. Die Wooden-Cog-Schweißrezepte für Netherit-Taucherausrüstung bleiben aus
   (Ausrüstung aus Ausrüstung, Packregel). Schmiedeeisenbarren als Stapel hinlegen: Der Stapel sieht nach Schmiedeeisen
   aus, nicht lila-schwarz. Train-Utilities-Erfolge kommen erst mit dem passenden Bauteil im Inventar. Im Server-Log
   stehen pro Start gut 100 statt rund 17.600 Fehlerzeilen, alle dokumentiert (`dev/check_log.py`).
10. **LAN.** Von einem zweiten PC: die IPv4-Adresse des Server-PCs eintragen (`server-ip=` bleibt leer, die Firewall
    muss Java im privaten Netz erlauben).

## 0.8.0 Nachtrag (Runde B, Pack 0.8.0, firmages-core 0.8.0)

Sechs Arbeitsstränge auf Deine Antworten zu „Firmament Ideen 2“ hin; die Listen für 0.7.2, 0.7.1 und 0.7.0 darunter
gelten sonst weiter. Server-Teil geprüft (`dev/poc-results.md`, Abschnitt „Round B (2026-10-13)“: GameTests, frische
Welt mit Dump, Blutmond- und Cave-Dweller-Probe; die Koordinaten-Probe fand einen Skriptfehler, behoben und im Boot
„0.8.0 final checks“ bewiesen, siehe Punkt 4), im Spiel noch offen. **Neues Profil importieren** (das
Zip aus `dev/release.md`, Abschnitt 1): Default Options kopiert die Shader-Voreinstellung, die Dunkelheits-Werte und die
neuen Tasten nur in ein frisches Profil. Die Test-Welt ist frisch (Dawn), auf dem Test-Server gelten schon
`torchTicks = 43200` und `jadeFromAge = "age_1"`.

**Server starten (korrigiert):** im Repo-Hauptordner den Ordner `test-server\world` löschen (frische Dawn-Welt), dann
`powershell -NoProfile -ExecutionPolicy Bypass -File dev\start_test_server.ps1`. Der Server läuft im Vordergrund, bis
Du `stop` in seine Konsole tippst. Nicht `python dev\run_server.py --hold 0`: das schickt gleich nach `Done` ein `stop`,
und sein `--timeout` beendet die JVM; `run_server.py` bleibt für automatische Läufe. Der Test-Server läuft ohne
Whitelist (`white-list=false`, nur Du testest; steht in `test-server\server.properties` noch `white-list=true`, einmal
`whitelist off` in die Konsole). `op <Name>` erst nach dem Dawn-Join-Check (`dev/release.md`, Abschnitt 2).

1. **Erster Start und Shader.** Das Spiel startet mit Iris und **Complementary Reimagined r5.9.3** an. **O** öffnet
   das Shader-Menü: dort auf **MakeUp Ultra Fast** wechseln oder Shader ganz aus; die Wahl bleibt nach einem Neustart.
   R (Shader neu laden) und K (Shader an/aus) von Iris sind nicht belegt, R bleibt die Wagen-Aktion, K das Questbuch.
   Prüfe mit beiden Packs: Create-Contraptions (ein fahrender Zug oder eine Mechanical Bearing) sehen richtig aus
   (Colorwheel; MakeUp läuft im Fallback-Modus), TFC-Pflanzen und Feldfrüchte leuchten nicht und wackeln nicht seltsam
   (Iris Block Compat ohne Wackel-Liste), Ars-Nouveau-Himmel (Skyweave) ohne Fehler. Auf einem Ad-Astra-Planeten oder
   Orbit und in The Origin schalten sich Shader von selbst ab, beim Rückweg wieder an. Beim Join kein Bildschirm und
   keine Chatzeile von Enhanced Celestials 2. Ein Blutmond (ab der Eisenzeit, Punkt 4) färbt den Himmel auch unter
   Complementary rot. Melde die FPS auf der RTX 3060 mit Complementary und mit MakeUp (Dynamic FPS senkt sie, wenn das
   Fenster im Hintergrund ist; AllTheLeaks und BadOptimizations laufen still mit).
2. **Titelbild „Constellation“.** Nachthimmel als Hintergrund, darüber alle 12 Sekunden eine Sternkarte des Schreins
   der nächsten Age (neun Folien, Überblendung, die vorige Age blass dahinter, Age-Name unten rechts). Die Karte
   bewegt sich mit der Maus etwas stärker als der Himmel. Wortmarke, die vier Knöpfe und die Versionszeile unten links
   („Firmament Ages 0.8.0“) wie in 0.7.2. Bitte bei 1920x1080 oder 2560x1440 mit GUI-Scale 2, 3 und Auto ansehen.
3. **Questbuch als Fenster (Deine Wahl A).** **K** öffnet das Questbuch als mittiges Fenster mit 75 % des
   Spielfensters, die Welt drumherum unscharf und abgedunkelt. Kapitelliste, Karte, Quest-Ansicht und Pop-ups (z. B.
   die Item-Liste einer Aufgabe, Rechtsklick-Menüs) bleiben im Fenster. Die Quest-Ansicht füllt das Fenster randlos:
   Titel und erster Buchstabe jeder Zeile sichtbar, kein Streifen rechts oder unten (der Fehler aus 0.7.2). Prüfe
   GUI-Scale 2, 3 und Auto, und ändere bei offenem Buch (auch bei offener Quest) die Fenstergröße oder den GUI-Scale:
   das Fenster folgt. Die Kapitelliste gleitet am linken Fensterrand herein und ist außerhalb nicht zu sehen; Zoomen
   mit dem Mausrad hält die Kartenmitte. Die Abstände zwischen Titel, Aufgaben, Trennlinie und Text sind enger.
   `config/firmages-client.toml` `[ui] questWindowPercent` (0 = Vollbild wie früher, 50 bis 100) gilt beim nächsten
   Öffnen. Questtexte: jeder Absatz ist jetzt ein Eintrag, FTB bricht ihn selbst auf die Spaltenbreite um. Bei
   GUI-Scale 3 und Auto endet eine Zeile nur noch am Spaltenrand oder am Absatzende (kein Flattersatz mehr).
4. **Härte der frühen Ages.**
   - **Betten ab der Eisenzeit.** In Dawn bis Bronzezeit kein Vanilla-Bett in EMI und keins herstellbar; das
     TFC-Strohbett setzt den Spawn, überspringt aber nie die Nacht (die Quest „Shelter“ sagt das). Ab `age_2` die 16
     Betten; mit zwei Spielern reicht einer im Bett, um die Nacht zu überspringen (50 %).
   - **Fackeln brennen 36 Minuten** statt 72 (eine gesetzte TFC-Fackel geht nach 36 echten Minuten aus).
   - **Echte Dunkelheit.** Ohne Shader (O-Menü aus): Nächte dunkel, je nach Mondphase (Neumond fast schwarz), Höhlen
     ohne Fackel stockdunkel; eine Fackel in der Hand leuchtet um Dich (LambDynamicLights). Mit Complementary bzw.
     MakeUp sorgen die mitgelieferten Shader-Einstellungen für dunkle Nächte und Höhlen. Melde, ob es zu dunkel oder
     zu hell ist, mit und ohne Shader.
   - **Keine Koordinaten bis zur Bronzezeit.** F3 zeigt in Dawn und Steinzeit keine XYZ-, Block-, Chunk- und
     Blickrichtungs-Zeilen; nach `/stage grant @s age_1` sind sie da (ohne Relog). Bitte zusätzlich `/gamerule reducedDebugInfo` abfragen:
     vor `age_1` `true`, danach `false`. Auf dem Server ist das bewiesen (`dev/poc-results.md`, „0.8.0 final checks“:
     `true` in Dawn und age_0, `false` ab `age_1`). Der Gravestone-Mod gibt beim Tod
     keinen Nachruf mit Grab-Koordinaten mehr.
   - **Blutmond erst ab der Eisenzeit.** `/ec lunarForecast` listet vor `age_2` keinen Blutmond; nach
     `/stage grant @s age_2` wird die Vorhersage neu gewürfelt und Blutmonde kommen wieder.
   - **Cave Dweller bis zur Eisenzeit.** In Dawn bis Bronzezeit jagt höchstens ein Cave Dweller in den Höhlen der
     Overworld; probieren: unter Y 40 im Dunkeln `/cave_dweller fast_forward minecraft:overworld 12000 12000`, das
     gibt Höhlengeräusche und einen Dweller. Nach `/stage grant @s age_2` ist ein vorhandener sofort weg,
     `/summon cave_dweller:cave_dweller` scheitert, derselbe Befehl gibt weder Dweller noch Geräusch.
   - **Jade erst ab der Bronzezeit,** zusammen mit EMI (bisher Jade ab der Steinzeit): in Dawn und Steinzeit kein
     Block-Overlay, nach `/stage grant @s age_1` ohne Relog da.
5. **Entfernt: Ping Wheel, Crash Assistant, TFCGenViewer.** In den Steuerungs-Einstellungen fehlen Ping Wheel und
   TFCGenViewer, nach einem Absturz kommt kein Crash-Assistant-Fenster. Ein altes Profil behält deren Configs, die kannst Du löschen.
6. **Erinnerung: TFC-Reiter (aus 0.7.2, noch ungeprüft).** Dawn nur Inventar, ab der Steinzeit der Kalender, ab der
   Eisenzeit das Klima direkt darunter, nie Ernährung und Field Guide, keine Lücken (Punkt 3 des 0.7.2-Nachtrags).
7. **Ein Item pro Material (Politur).** EMI zeigt jetzt auch Amethyst nur als TFC-Edelstein, Quarz nur als
   Netherquarz, Salpeter nur als TFC-Pulver, Gusseisen und Sterlingsilber nur von TFC, Holzstaub nur von IE,
   Amethyst-Staub nur von Occultism und Andesit-Blech nur von Create Deco; Rezepte, die das versteckte Item ausgeben,
   geben das sichtbare. Unpolierter Quarz aus der Quarzader plus Schleifpapier gibt jetzt Netherquarz, und
   Netherquarz öffnet das Twilight-Forest-Portal (Quest „Into the Twilight“). Die Nebenquest „Mystical Agriculture“
   will statt 8 Seelenstein nur noch den Blick auf einen Seelenstein-Körper (ohne Behutsamkeit fällt Seelenstein als
   Bruchstein). Prüfe: in EMI zeigen `#c:gems/quartz` und `#c:gems/amethyst` je ein Item; das Schleifpapier-Rezept
   zeigt Netherquarz; das Portal zündet mit Netherquarz.

Melde: Punkte 1 bis 7 ja/nein, FPS mit Complementary und MakeUp, Dunkelheit zu dunkel / passt / zu hell (mit und
ohne Shader), und ob die Questtexte bei GUI-Scale 3 und Auto glatt umbrechen.

## 0.7.2 Nachtrag (Feedback 2, Pack 0.7.2, firmages-core 0.7.2)

Fünf Änderungen auf Deine Screenshots und Entscheidungen hin; die 0.7.1- und 0.7.0-Listen darunter gelten sonst
weiter. Server-Teil geprüft (`dev/poc-results.md`, Abschnitt „Feedback 2 (2026-10-13)“: GameTests, frische Welt mit
Dump und Adern-Scan), im Spiel noch offen. **Neues Profil importieren** (das Zip aus `dev/release.md`, Abschnitt 1); in
einem alten Profil fehlt sonst das neue firmages-core-Jar oder `config/create-client.toml`. Die Test-Welt ist frisch
(Dawn).

1. **Titelbild (Punkt 3 der 0.7.0-Liste).** Bitte bei 1920x1080 oder 2560x1440 je mit GUI-Scale 2, 3 und Auto
   ansehen. Nur noch Singleplayer, Multiplayer, Options, Quit (8 px höher als bisher), Wortmarke, Diashow und unten
   links die Versionszeile „Firmament Ages 0.7.2“, jetzt vollständig lesbar. **Weg:** Sprache und Barrierefreiheit
   (beides weiter unter Options; das blendet firmages-core aus, sind sie noch da, läuft das alte Jar) und Creates
   Brillen-Knopf. **Bleibt:** „Copyright Mojang AB. Do not distribute!“ unten rechts, das blendet FancyMenu absichtlich
   nie aus (Entscheidung offen, siehe `dev/decisions-while-away.md`). Ursachen und Prüfung: `python dev/check_menus.py`.
2. **Pausemenü (Punkt 11, ersetzt die Liste dort).** Nur noch gestylt **Back to Game**, **Options** und mit etwas
   Abstand **Disconnect** (im Einzelspieler „Save and Quit to Title“), mittig untereinander. Weg: Advancements,
   Statistics, Give Feedback, Report Bugs, Server Links, Open to LAN (im Mehrspieler Player Reporting), Mods, Creates
   Brille. Das 0.7.0-Layout griff gar nicht (falsche FancyMenu-IDs), darum sahst Du die Vanilla-Knöpfe. Ist die
   Brille in einem älteren Profil noch da: in `config/create-client.toml` `mainMenuConfigButtonRow` und
   `ingameMenuConfigButtonRow` auf 0 prüfen.
3. **TFC-Reiter im Inventar nach Age.** **Dawn:** nur der Inventar-Reiter. **Ab der Steinzeit**
   (`/stage grant @s age_0`): darunter der Kalender. **Ab der Eisenzeit** (`age_2`): darunter das Klima, direkt unter
   dem Kalender. **Nie:** Ernährung und Field Guide (Buch). Die Reiter rücken ohne Lücke nach oben, auch in den
   Kalender- und Klima-Seiten selbst und im Kreativ-Inventar, falls TFC dort Reiter zeigt. Eine neue Age gilt beim
   nächsten Öffnen des Inventars (offenes Inventar einmal schließen). Einstellbar pro Welt in `firmages-server.toml`
   unter `[ui]` (`calendarTabFromAge`, `climateTabFromAge`, `nutritionTabFromAge`, `fieldGuideTabFromAge`; eine Age-ID
   oder `never`). Der Server öffnet eine gesperrte Seite auch nicht, wenn ein Client sie anfragt (GameTest).
4. **Simple Voice Chat ist weg.** Beim Join kein Voice-Chat-Hinweis, keine Mikrofon-Abfrage, keine V-Taste; Alt+M ist
   frei. Ein altes Profil behält `config/voicechat/`, das kannst Du löschen.
5. **Mystical-Agriculture-Adern.** Inferium-Erz (alle Gesteine, y -32 bis 100), Prosperity-Erz (magmatisch und
   metamorph, tief, y -64 bis 30) und Seelenstein mit etwas Soulium-Erz (nur in Basalt) kommen als TFC-Adern; bis zur
   Informationszeit sehen sie aus wie ihr Gestein und lassen auch nur Gestein fallen. Mit `/stage grant @s age_5` eine
   Ader suchen und abbauen: Fels, kein Treffer im Prospektor. Nach `/stage grant @s age_6`: der Block zeigt das Gestein
   mit MAs grünem bzw. hellblauem Erz-Overlay (achte auf schwarze Pixel um das Overlay), gibt Inferium-Essenz bzw.
   Prosperity-Splitter, der Prospektor findet ihn. Seelenstein liegt nur in Basalt, und Basalt ist selten: um den
   Spawn der Testwelt gab es keinen, 2000 Blöcke weiter östlich und 4000 Blöcke südlich schon. Seelenstein taucht ab
   der Informationszeit mit seinen Baublöcken in EMI auf. Die Nebenquest „Mystical Agriculture“ der Informationszeit
   verlangt jetzt Essenz, Splitter und Seelenstein. Melde, ob Du die Adern in einer Sitzung mit dem Prospektor findest.
6. **Quest-Ansicht, engere Absätze.** Öffne mit **K** eine Quest, zuerst „Welcome to Firmament Ages“. Absätze stehen
   jetzt 14 statt 18 Pixel auseinander (bei GUI-Scale 3 also 42 statt 54 Bildschirmpixel), der Abstand vom kursiven
   Untertitel zum Text ist derselbe; Zeilen innerhalb eines Absatzes bleiben bei 9 Pixeln, enger geht es mit der
   Minecraft-Schrift nicht. Zu eng oder zu weit: in `config/firmages-client.toml` unter `[ui]` `questParagraphGap`
   ändern (0 bis 9, Standard 4; 9 ist der alte Abstand), die Quest schließen und neu öffnen, kein Neustart (noch
   ungeprüft). **Noch nicht behoben:** die Vollbild-Ansicht sitzt weiter 10 Pixel zu weit links und oben (Titel und
   erster Buchstabe jeder Zeile abgeschnitten, rechts und unten ein Streifen). Ursache ist Code in FTB Library, kein
   Theme und keine Config; die Behebung hängt an Deiner Wahl zwischen A (Quest-Fenster bei 75 % des Bildschirms) und
   B (Vollbild repariert), siehe `Modpack-Planung/research-raw/fb2-quest-screen.md`, Abschnitte 5 und 6.

Melde: Punkte 1 bis 6 ja/nein, Absatzabstand passt / enger / weiter, A oder B für die Quest-Ansicht, und ob die
Copyright-Zeile bleiben darf.

## 0.7.1 Nachtrag (Entscheidungen 2026-10-13 spät, Pack 0.7.1, firmages-core 0.7.1)

Drei Dinge ändern die 0.7.0-Liste unten; der Rest gilt unverändert. Server-Teil statisch geprüft
(`dev/poc-results.md`, Abschnitt „Decisions 2026-10-13 late“), im Spiel noch offen.

1. **Tasten (Punkt 4).** **Tab** bleibt die Spielerliste, **Y** bleibt Cataclysm „Ability“. Umgelegt:
   „Physics Staff Rotate Mode“ (Create Simulated) liegt jetzt auf **Komma**, „Cataclysm Chestplate Ability“ auf
   **Apostroph** (auf der deutschen Tastatur die Taste **Ä**). Controlling, Filter „Conflicts“: Tab und Y tauchen nicht
   mehr auf. Sicher greift das nur in einem frischen Profil (Default Options); in einem älteren Profil stehen die zwei
   Tasten noch auf Tab und Y, dann in der Steuerung „Reset“ drücken oder von Hand umlegen.
2. **Jade ab der Steinzeit, EMI erst ab der Bronzezeit (Punkte 13 und 14).** Nach `/stage grant @s age_0`: Jade zeigt
   Blöcke, **EMI bleibt weg** (auch mit Strg+O). Nach `/stage grant @s age_1`: EMI erscheint ohne Neuverbinden; die
   EMI-Prüfungen aus Punkt 13 (keine Werkbank, kein Ofen, keine Fackel, kein Eimer, keine nie erreichbaren Items)
   machst Du dort. Bestehende Welten behalten ihre `firmages-server.toml`; auf dem Test-Server steht
   `emiFromAge = "age_1"` schon drin, eine neue Welt bekommt es als Standard.
3. **Versteckt:** die TFC-Stahlstufe (`tfc:metal/block/steel_slab` samt verwitterten Stufen) und IEs Resonanz-Observer
   (Chunk Loader) fehlen in EMI; die IE-Stahlstufe ist da (ab der Industriezeit). MekaSuit- und Meka-Tool-Module sind
   in EMI sichtbar, sobald ihre Age erreicht ist (Modul-Basis ab der Informationszeit, aus TFC-Bronzebarren).

## 0.7.0 Immersion (Stand 2026-10-13, Pack 0.7.0, firmages-core 0.7.0, nach Review)

Diese Phase heißt **Immersion**: Ladebildschirm, Titelbild, Standard-Optionen, stiller Join, EMI und Jade erst ab
der Steinzeit, HUD-Age-Anzeige, Willkommen, Questbuch im Firmament-Stil mit versteckten Kapiteln, TFC ohne Durst und
ohne Ernährungsstrafe. Der Server-Teil ist auf dem Test-Server geprüft (`dev/poc-results.md`, Abschnitt „Immersion“);
alles unten kann nur ein echter Client zeigen. Reihenfolge so lassen: die ersten Punkte gibt es nur beim allerersten
Start eines frischen Profils.

### Vorbereitung

1. Zip bauen und **als neues Profil** importieren (`dev/release.md`, Abschnitte 1 und 2). Ein bestehendes
   Profil behält seine `options.txt` und `emi.css`; Default Options greift nur beim ersten Start. Wer das alte Profil
   nimmt: dort `config\emi.css` löschen und die Tasten unten von Hand prüfen.
2. Test-Server starten (`dev/release.md`, Abschnitt 2), **frische Welt**: `test-server\world` löschen, dann
   `powershell -NoProfile -ExecutionPolicy Bypass -File dev\start_test_server.ps1` (läuft, bis Du `stop` tippst;
   `run_server.py` ist nur für automatische Läufe, `--hold 0` stoppt den Server gleich nach `Done`). Der Server läuft
   ohne Whitelist (`white-list=false`), noch **kein** `op` (sonst kommen RCON- und Op-Meldungen in den Chat).

### Was Du anschaust, in dieser Reihenfolge

1. **Erster Start, frühes Ladefenster.** Statt des roten NeoForge-Fensters: dunkler Firmament-Hintergrund, Wortmarke,
   goldener Balken, kein Log-Text. Ist der allererste Start noch rot, ein zweites Mal starten und dort prüfen
   (`config/fml.toml` muss `earlyWindowProvider = "drippy_early_window"` enthalten).
2. **Spätes Laden (Resource-Reload).** Wortmarke und goldener Fortschrittsbalken auf dem Hintergrundbild, kein
   Mojang-Logo.
3. **Titelbildschirm.** Diashow der neun Shrine-Bilder (etwa 9 s pro Bild, Überblendung), Wortmarke oben links, vier
   Knöpfe links (Singleplayer, Multiplayer, Options, Quit) auf jedem Bild lesbar, unten links „Firmament Ages 0.7.0“.
   Es fehlen: Mods, Realms, Sprache, Barrierefreiheit, Splash-Text, die Zeile „NeoForge (N mods)“. Bei GUI-Scale 3 und
   „Auto“ auf Überlappungen achten. Fenstertitel „Firmament Ages“ mit Pack-Icon.
4. **Optionen eines frischen Profils.** GUI-Scale 3, Fenster (kein Vollbild), dunkler Ladehintergrund, keine
   Tutorial-Toasts, keine Barrierefreiheits-Abfrage, keine Mehrspieler-Warnung. **Steuerung:** Sprint = Shift links,
   Schleichen = Strg links, Questbuch = K, „Show Age (hold)“ = **H** (Kategorie Firmament Ages), Fortschritte (L)
   ungebunden. Controlling, Filter „Conflicts“: erwartet sind nur noch die Halte-Modifikatoren auf Shift/Strg
   (Create, AE2, Jade, Mekanism, Apotheosis, Hypertube) und zwei Restkonflikte, die das Review gefunden hat:
   **Tab** (Spielerliste und „Rotate mode“ von Create Simulated) und **Y** (Cataclysm „Ability“ und
   „Chestplate ability“). Ab 0.7.1 umgelegt (siehe Nachtrag oben). Jeder andere Konflikt: melden.
5. **Beitreten (frischer Spieler).** Kein Bildschirm „Enhanced Celestials 2“, keine Chat-Zeilen von Mods (Ars Nouveau
   Lichter oder Makeship, Train Utilities, Backups, ProgressiveStages,
   keine Zeile „Reloaded with no KubeJS errors“). Kein „Worn Notebook“ von Ars Nouveau im Inventar. Die Verbindung
   klappt ohne Fehler „missing channels“ (FancyMenu, Drippy, Melody, Konkrete und CQA sind nur auf dem Client).
   Nach etwa 2 s Titel „Firmament Ages“ mit Untertitel „The sky remembers. Kindle the first spark.“, etwa 5 s später
   der Willkommensbildschirm mit drei Feldern (The Ages, Your Path mit der K-Taste, Caelum Watches). „Show my path (K)“
   öffnet das Questbuch, Esc führt zurück ins Spiel. Bei schmalem und breitem Fenster ansehen.
6. **Neu verbinden.** Der Willkommensbildschirm kommt **nicht** noch einmal (auf dem Server einmal pro Spieler gespeichert).
   Erneut ansehen: `/firmages welcome reset <Name>` (op) und neu verbinden.
7. **HUD-Age-Anzeige.** Oben links eine Zeile mit Icon: „Dawn · Goal: the First Spark“. Nach etwa 6 s gleitet sie nach
   oben und blendet aus; solange **H** gehalten wird, ist sie da und blendet nach dem Loslassen in 1 s aus. Bei F1, F3
   und offenen Bildschirmen unsichtbar.
8. **Dawn: nichts von der Maschinerie.** EMI fehlt ganz, auch mit Strg+O; kein Rezeptbuch-Knopf im Inventar; Jade zeigt
   beim Anschauen eines Blocks nichts. Item-Tooltips ohne Modnamen (auch kein blauer „Minecraft“). Im Inventar
   oben links **keine FTB-Knöpfe** (Seitenleiste ist aus, `config/ftblibrary-client.snbt`, neu im Review). R oder U
   über einem Item im Inventar öffnet nichts.
9. **Questbuch (K).** Firmament-Look: Nachthimmel mit dem Shrine der Age hinter den Quests, Age-Banner über der oberen
   Reihe ohne Überlappung, Quest-Formen (gold für Ziel, Rune für Einstieg und Keystones, Glut für Nebenquests),
   bewegter Sternhimmel (Certain Questing Additions), der beim Scrollen und Zoomen mitgeht. Sichtbar sind **nur
   Welcome, The Firmament und Dawn**; die Ages-Gruppe zeigt keine grauen Einträge. Die sieben Welcome-Texte lesen
   (kein Wort über Mods oder Maschinerie). Danach `logs\latest.log` des Profils nach `certain_questing_additions`,
   `CqaMixinConfigPlugin` und `ShaderInstance` durchsuchen: keine Fehler.
10. **Fortschritte und Toasts.** Einen Baumstamm abbauen oder etwas craften: kein Advancement-, Rezept- oder
    Tutorial-Toast, keine Chat-Zeile „has made the advancement“. FTB-Quests-Toasts erscheinen weiter.
11. **Pausemenü (Esc).** Gestylte Knöpfe (Back to Game, Statistics, Options, Open to LAN, Disconnect); es fehlen Mods,
    Advancements, Feedback und Report Bugs, ohne Lücke (Statistics volle Breite). Hier wirken FancyMenu und
    firmages-core beide auf dieselben Knöpfe: genau auf verrutschte oder doppelt verschobene Zeilen achten.
12. **TFC.** Kein Durstbalken; im Inventar kein Ernährungs-Tab, der Klima-Tab rückt nach und alle Tabs gehen, auch mit
    dem Rezeptbuch-Platz. Essens-Tooltips ohne Nährwertzeilen, Verfall und Eigenschaften stehen da. Die TFC-Lebensanzeige
    fällt nie unter den Startwert (etwa 850 HP-Anzeige), auch nach einer Weile einseitigem Essen.
13. **Steinzeit freischalten** (`op <Name>`, dann `/stage grant @s age_0`). Ohne Neuverbinden: HUD erscheint wieder mit
    „Stone Age · Goal: Hearthstone“, EMI erscheint, Jade zeigt Blöcke (ohne Modnamen-Zeile), das Kapitel Stone Age
    taucht im Questbuch auf. In EMI fehlen Werkbank, Ofen, Fackel und Eimer (spätere Age) und nie erreichbare Items;
    Quest-Icons solcher Items (Multiblöcke, Stargate) werden trotzdem gezeigt.
14. **Weitere Age** (`/stage grant @s age_1`): genau ein neues Kapitel (Bronze Age), HUD mit dem neuen Ziel. Im
    Chat steht dabei die ProgressiveStages-Zeile „Age 1: Bronze Age - …“ (siehe unten, Entscheidung offen).
15. **Neue Belegung im Alltag.** In einer Create-Hypertube mit **Shift** (jetzt Sprint) aussteigen; mit **Strg**
    schleichend an AE2-Kabel und Create-Blöcken bauen. Sag, ob sich etwas falsch anfühlt.

### Was Du meldest

- Pro Punkt ja oder nein, bei Nein ein Screenshot.
- `logs\latest.log` des Client-Profils, wenn etwas fehlt oder rot ist; bei einem Absturz den Crash-Report.
- Ob Dir an Texten (Willkommen, HUD, Welcome-Kapitel) etwas nicht gefällt.

### Offen nach dem Review (Deine Entscheidung, kein Client-Test)

Stehen jetzt in `dev/offene-punkte.md` (die Stage-Chatzeile ist gelöscht, die Versionszeile setzt `dev/release.ps1 -Version`).

## Frühere Phasen

Die Tests von firmages-core 0.3.0 bis 0.6.1 liegen in `../Modpack-Planung/archive/dev/client-test-brief-0.3-0.6.md`; was davon weiter gilt, steht in `dev/release.md`, Abschnitt 2.
