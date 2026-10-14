# Client-Test

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
    ohne Einladung darin (Chatzeile „You join Firmament. Its Ages are yours.“). Auf Deiner Welt (Du allein, ohne Party,
    age_9) ändert sich nichts; ein zweiter Spieler liest „HiighHopes holds this world's Ages outside a party ...“,
    siehe `dev/offene-punkte.md`. The Origin: Steht nur ein Teil am Altar, zeigt die Aktionsleiste „The altar waits for N more: ...“;
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
   (stehen lassen) und - (wegschlagen), dann der Axtkopf über einem Stock im 2x2-Feld. Unten im Kapitel steht die neue
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
