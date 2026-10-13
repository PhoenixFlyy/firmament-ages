# Client-Test

## Nachtrag: Quest-Ansicht, engere Textabstände (firmages-core nach 0.7.1)

Öffne mit **K** eine Quest, zuerst „Welcome to Firmament Ages“. Absätze stehen jetzt 14 statt 18 Pixel auseinander
(bei GUI-Scale 3 also 42 statt 54 Bildschirmpixel), der Abstand vom kursiven Untertitel zum Text ist derselbe; Zeilen
innerhalb eines Absatzes bleiben bei 9 Pixeln, enger geht es mit der Minecraft-Schrift nicht. Wenn Dir der Absatz zu
eng oder zu weit ist: in `config/firmages-client.toml` unter `[ui]` den Wert `questParagraphGap` ändern (0 bis 9,
Standard 4; 9 ist der alte Abstand), die Quest schließen und neu öffnen, kein Neustart. **Noch nicht behoben:** die
Vollbild-Ansicht sitzt weiter 10 Pixel zu weit links und oben (Titel und erster Buchstabe jeder Zeile abgeschnitten,
rechts und unten ein Streifen). Ursache ist Code in FTB Library, kein Theme und keine Config; die Behebung hängt an
Deiner Wahl zwischen A (Quest-Fenster bei 75 % des Bildschirms) und B (Vollbild repariert), siehe
`Modpack-Planung/research-raw/fb2-quest-screen.md`, Abschnitte 5 und 6. Melde: Absatzabstand passt / enger / weiter,
und A oder B.

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

1. Zip bauen und **als neues Profil** importieren (`dev\cf\LOKAL-TESTEN.md`, Schritte 1 und 2). Ein bestehendes
   Profil behält seine `options.txt` und `emi.css`; Default Options greift nur beim ersten Start. Wer das alte Profil
   nimmt: dort `config\emi.css` löschen und die Tasten unten von Hand prüfen.
2. Test-Server starten (`LOKAL-TESTEN.md`, Schritt 3), **frische Welt** (`python dev\run_server.py --wipe-world --hold 0`),
   `whitelist add <Name>`, noch **kein** `op` (sonst kommen RCON- und Op-Meldungen in den Chat).

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
   Lichter oder Makeship, Train Utilities, Crash Assistant, Simple Voice Chat „Press V“, Backups, ProgressiveStages,
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

- **Eigene Schrift** (Entscheidung 2: „custom logo/buttons/font“) ist **nicht** umgesetzt: Logo und Knöpfe sind
  eigene Bilder, die Texte nutzen die Minecraft-Schrift. Willst Du eine eigene Schrift (ganzes Spiel oder nur Titel)?
- Entschieden (2026-10-13 spät, 0.7.1): Jade ab der Steinzeit, EMI ab der Bronzezeit (`ui.jadeFromAge = "age_0"`,
  `ui.emiFromAge = "age_1"` in `firmages-server.toml`, so auch der Standard für neue Welten).
- Ernährung: Untergrenze ist TFCs Startwert (0,85, Lebensanzeige etwa 850), nicht der volle Vanilla-Wert (1,0).
  Volle Gesundheit ab Start: in `tfc-server.toml` `nutritionDefaultHealthModifier = 1.0`.
- Jede neue Age schreibt die ProgressiveStages-Zeile „Age N: …“ in den Chat (`unlock_message` in
  `config/progressivestages/stages/age_N.toml`). Behalten oder leeren?
- Erledigt (Nachtrag 2026-10-13): Quest „Advanced TFC Tech“ verlangt jetzt den geformten Thresher (Blick darauf);
  `ftbquests:loot_crate_opener` und `ftbquests:task_screen_configurator` sind versteckt. Im Client prüfen: die
  Quest schließt beim Blick auf einen geformten Thresher ab, beide Items fehlen in EMI.
- Crash Assistant: `help_link` steht noch auf `CHANGE_ME` (Discord-Einladung fehlt).
- Die Versionszeile im Titelbild ist fester Text (`config/fancymenu/customization/title_screen_layout.txt`), seit
  0.7.1 „Firmament Ages 0.7.1“; bei jedem Release mit hochziehen.
- Die Release-Dateien in `dev/exports/` (0.7.0) sind vor dem Review-Commit gebaut; vor dem Upload
  `dev/release.ps1 -Version 0.7.0` neu laufen lassen, damit `config/ftblibrary-client.snbt` drin ist.

## Frühere Phase: Client-Test: Age-Übergang am Schrein (firmages-core 0.3.0)

Ausführlich: `dev/dev-client.md`, Abschnitt „Age-Übergang testen“. Hier die Kurzfassung.

### Was Du tust

1. `dev\serve.bat` starten. Test-Server syncen und mit `tools\jdk-21` starten (Befehle in dev-client.md, Schritt 1; nicht `run.bat`).
2. Server-Konsole: `whitelist add <Name>`, `op <Name>`. Prism-Instanz „Firmament Ages Dev“ starten, `localhost` verbinden.
3. `/stage grant @s age_0` (kurze Zeremonie), dann `/gamemode creative`.
4. `/give @s firmages:shrine_heart`, `/give @s firmages:offering_plinth`, Herz auf flachen Boden setzen.
5. Herz mit leerer Hand benutzen (ohne Schleichen): Chat nennt fehlende Blöcke, Geistervorschau erscheint.
6. Herdkreis bauen: 8 Bruchstein (`tfc:rock/cobble/granite`) direkt ums Herz, an den 4 Ecken des 5x5 je 2 Stämme (`tfc:wood/log/oak`) mit `tfc:thatch` obendrauf, Sockel 2 Blöcke vor dem Herz in der Mitte einer Seite. `/firmages shrine status` muss „Ring 0 ... complete“ zeigen.
7. `/give @s firmages:hearthstone`, `/give @s tfc:firestarter`, `/gamemode survival`. Hearthstone auf den Sockel legen.
8. Herz einmal mit dem Feuerstarter benutzen (brennt danach).
9. Hand leer, schleichen, Benutzen am Herz gedrückt halten (max. 6 Blöcke Abstand), ca. 10 s.
10. Danach: einen Stamm abbauen und wieder setzen; zum Schluss ein zweites Herz woanders setzen.

### Worauf Du achtest

- Sitzt die Geistervorschau genau um das Herz (nicht einen Block zu hoch/tief)?
- Zeremonie: Musik aus, Lichtstrahl (bernstein), Himmel orange, **genau ein** Titel „The Bronze Age dawns“, Chat „Caelum: A fire that keeps its shape. I see you.“, danach Segen „Hearthward“.
- Reload-Einfrieren nach ca. 3 s: Aktionsleiste „The world realigns...“, Effekte laufen weiter, kein Kick.
- EMI ohne Relog: `create:andesite_alloy` hat jetzt ein Rezept. K-Screen: Ziel-Quest „Offer the Hearthstone at the Shrine“ erledigt (sobald alle Keystones fertig sind).
- Hearthstone schwebt sichtbar über dem Sockel und lässt sich nicht abbauen. Stamm weg: „The shrine is broken...“, Age bleibt.

### Was Du meldest

- Pro Punkt oben: ja/nein, ungefähre Zeit, Auffälliges (gern Screenshot).
- Reload-Dauer aus `/firmages ages` und ob EMI danach ruckelte.
- Fehler im Chat; bei Problemen `test-server\logs\latest.log`, bei Client-Absturz den Crash-Report aus der Prism-Instanz.

### Seit dem Schreiben geändert (Stand 2026-10-01, firmages-core 0.3.3)

Die Schritte oben gelten unverändert; Ring 0, Hearthstone, Zeremonie und Segen „Hearthward“ sind gleich geblieben, nur heißt die Mod-Version jetzt 0.3.3 statt 0.3.0. Der Reload dauert auf dem Test-Server jetzt etwa 6,2 bis 6,6 s statt gut 8 s (2 Test-Spieler, alle Ages; der erste Reload nach dem Start bis etwa 8 bis 10 s). Bei Age-Vergaben, die auch Mob- oder Helfer-Stages ändern (gemessen bei age_4 und age_6), friert der Server zusätzlich etwa 2,6 s ein, bevor der Reload beginnt; dann endet der Reload erst um 12 s nach Zeremonie-Beginn, also knapp nach der 12-s-Zeremonie. Melde deshalb bitte auch, ob das Einfrieren gleich nach dem Titel kommt und ob „The world realigns...“ bis zum Ende sichtbar bleibt. Neu sind die Ringe 3 bis 8 (Spirit Circle bis Quantum Ring, 11x11 bis 21x21) mit eigenen Riten und Segen sowie die Signatur-Items Humming Core, Data Matrix, Star Chart und Quantum Core. Wenn Du Zeit hast: `/firmages shrine status` und die Geistervorschau eines späten Rings ansehen und den Tooltip „Signature item of the … Age“ an einem Signatur-Item prüfen (die neuen Items haben noch keine Textur).

### Neu ab firmages-core 0.4.0: The Origin, Endboss, Reaktor (Stand 2026-10-01)

Über den Schrein hinaus kannst Du jetzt drei Dinge im Client testen, am besten zu zweit und in Creative-Vorbereitung. **The Origin:** `/stage grant @s age_9`, dann `/firmages origin tp` (ohne age_9 muss „This dimension is locked!“ kommen). Achte auf End-Himmel, dunkles Licht, die schwebende Scheibe mit Altar, neun Inseln und das Rück-Stargate mit DHD; vom DHD aus nach Hause wählen und prüfen, ob Du heil ankommst. Wer Zeit hat, baut in der Overworld ein Classic Stargate (`/give` die Teile) und wählt 9-16-21-33-2-37 plus Ursprungssymbol (steht auch im Tooltip von „Coordinates of The Origin“). **Endboss:** Alle Online-Spieler (nicht Zuschauer) stellen sich höchstens 6 Blöcke um den Altar. Dann öffnet sich das Gateway mit drei Wellen (Maledictus, Ignis, beide), danach erscheint „The Primordial“ mit Phasen bei 2/3 und 1/3 Leben. Melde, wie lange der Kampf mit Chaotic-Ausrüstung dauert (auf dem Server zog ein 500-Schaden-Treffer nur etwa 8 Leben ab; er ist evtl. zu zäh), ob die FINALE-Zeremonie (Titel „Beyond the Firmament“, Stimme, Partikel, Himmel, Blitze) genau einmal läuft und ob im K-Screen das Finale abgehakt ist. `finale_won` lässt sich nicht zurücknehmen; `/firmages origin reset` startet nur den Kampf neu, also bitte in einer Testwelt. **Reaktor:** Einen Draconic Reactor in Creative bauen, den Reactor Controller an einen Stabilisator setzen, eine Truhe mit erweckten Draconium-Blöcken daneben und Redstone-Draht daran. Der Controller füllt den kalten Reaktor auf und meldet READY mit Redstone 15; laden und starten musst Du selbst. Im Betrieb muss er bei 80 % Umwandlung herunterfahren, nach dem Abkühlen Chaos-Splitter herausnehmen, Brennstoff nachfüllen und wieder READY melden, aber **nie** selbst starten. Status: leere Hand am Controller oder `/firmages reactor status`. Teste auch Rohre an Stabilisatoren (Brennstoff rein, Chaos raus, nur im kalten Zustand).

### Neu ab firmages-core 0.5.0: Texturen, Keystone-Leihe, Segen, Origin-Sperre (Stand 2026-10-01)

Alles hier lief nur auf dem Server ohne Bild; was folgt, kann nur ein Client zeigen.

**Installieren wie ein Freund** (zuerst, dann hast Du gleich die Testinstanz):

1. `dev\serve.bat` starten (Pack auf `http://localhost:8080/pack.toml`).
2. `powershell -NoProfile -ExecutionPolicy Bypass -File dev\make_friends_zip.ps1 -PackUrl http://localhost:8080/pack.toml` baut `dev\exports\FirmamentAges-Prism.zip`. Ohne `-PackUrl` zeigt die Zip auf `main` auf GitHub; `main` ist noch das leere Gerüst, das klappt also erst nach dem Merge von `dev` nach `main` und dem Push.
3. In Deinem normalen Prism (nicht der Dev-Instanz): *Add Instance → Import*, die Zip wählen, nicht entpacken. Dann den sechs Schritten aus `dev/friends/README-FRIENDS.md` folgen (Java automatisch, 8 GB, Start).
4. Achte darauf: Lädt Prism Java 21 selbst? Öffnet sich vor dem Spiel das packwiz-Fenster, und endet es ohne „Hash invalid!“? Startet das Spiel mit rund 190 Mods ins Hauptmenü? Ein zweiter Start soll nur kurz prüfen und nichts neu laden.
5. Ersatzweg ohne Prism-Zip: `dev\release.ps1` baut `dev\exports\FirmamentAges-0.1.0.mrpack`; den in Prism oder der Modrinth App importieren (ohne automatische Updates). Beide Exporte nur privat weitergeben.

**Texturen:** EMI nach `firmages:` filtern. Alle 18 Items (Hearthstone bis Origin Coordinates, Zink- und Bismutstaub) müssen eine eigene Textur haben, keine lila-schwarzen Würfel. Herz, Sockel, Origin-Altar und Reactor Controller setzen: eigene Seiten und Oberseiten? Das entzündete Herz pulsiert oben (4 Bilder), der erweckte Sockel hat einen Goldring, der bereite Controller ein weiß-violettes Feld. Arcane Keystone und Awakened Keystone sehen sich sehr ähnlich (nur Funken): sag, ob Du sie im Inventar auseinanderhältst.

**Jade:** Auf das Herz schauen: erweckte Stufen, „next ring ready“ oder nicht, kalt. Auf einen Sockel: Age, Relikt oder Opfergabe, „lent“ oder leer.

**Keystone-Leihe (age_8):** Schleichen mit leerer Hand auf Sockel 4 (Spirit Circle) gibt Dir den Arcane Keystone. Der Sockel bleibt leer, glüht weiter und lässt sich im Survival nicht abbauen. Der Quantum Core wird jetzt abgelehnt. Mit dem Keystone das echte Marid-Ritual (Occultism) machen, den Awakened Keystone zurück auf Sockel 4 legen; danach muss der Quantum Core angenommen werden. Melde, ob die Chat-Texte verständlich sind.

**Segen:** Im Schutzkreis des Herzens (Radius 12 + 4 je Stufe, bei age_8 also 40) wirken: +5 % Tempo, +5 % Leben je Segen (Iron Will, Clarity, ab age_9 Resolve), +5 % Abbautempo, +0,5 Reichweite, weniger Fallschaden, +10 % XP. Prüfen mit `/attribute @s minecraft:generic.max_health get` innerhalb und 60 Blöcke außerhalb. Wichtig: Passen die Extraherzen zu TFCs Ernährungs-Leben, oder springt die Herzanzeige? Bei kaputtem Schrein (ein Ringblock weg) verschwinden die Segen, nach der Reparatur kommen sie nach ein paar Sekunden zurück.

**Energie-Ritus (Ring 5):** Die vier IE-HV-Kondensatoren der Krone mit echten Kabeln auf zusammen 1.000.000 FE laden. `/firmages shrine status` zeigt den Stand; erst dann wird das Gebet erhört.

**Origin-Sperre:** Ohne age_9 in Survival darf kein Weg nach The Origin führen: `/firmages origin tp`, Mekanism-Teleporter, Ars-Warp-Schriftrolle, Stargate. In Creative kommst Du durch. Mit age_9 klappt der Weg über das Stargate.

**The Origin und Endkampf:** Wie viele Endermen, Endermapteras, Ignited Revenants und Ender Golems erscheinen in der beleuchteten Arena (soll wenige, keine Tiere)? Den Kampf gegen The Primordial einmal echt durchspielen: Auf dem Server dauerte Dauerschlagen 4 min 46 s, echt erwartet etwa 7 Minuten. Erscheinen die Phasen-Untertitel bei 2/3 und 1/3, kommen die Helfer, und verschwinden sie beim Tod? FINALE genau einmal.

**Milch:** Ein Rezept mit TFC-Holzeimer voll Milch (z. B. der Create-Kuchen oder ein Ars-Rezept) in EMI ansehen und einmal craften.

### Neu ab firmages-core 0.6.0: Weihe, Wartungsmodus, Texturen v2 (Stand 2026-10-01)

Auf dem Server ohne Bild geprüft (`dev/poc-results.md`, „M7 consecration and textures v2“); hier nur, was ein Client zeigen kann.

**Weihe ansehen:** Ring 0 bauen, Hearthstone opfern, Herz entzünden, beten. Nach dem Reload (etwa 1 s später) werden die Blöcke über etwa 6 s vom Herz nach außen und von unten nach oben zu hellem Himmelsmarmor mit goldenen Adern, mit End-Rod- und Verzauberungs-Partikeln und einem Klang. Achte darauf: Leuchtet der orange Akzent (Ecken, Rune) auch im Dunkeln (voll hell)? Flackert das Akzent-Overlay (Z-Fighting)? Säulen: Stirnseite mit Goldring, Seiten mit Kanneluren. Bei höheren Ringen: Glas durchsichtig mit Akzent, Gerüst mit Löchern, Lampen hell (Licht 15).

**Unzerstörbar:** Im Survival mit Spitzhacke auf einen geweihten Block: kein Abbau-Riss, kein kurz verschwindender Block, Hinweis in der Aktionsleiste.

**Wartung:** Schleichen und mit leerer Hand auf das Herz schlagen (Linksklick). Das Herz bekommt einen Gerüst-Käfig, die Aktionsleiste zählt 60 s herunter. Jetzt bricht ein geweihter Block mit Spitzhacke (normaler Abbau-Riss) und lässt das Original fallen (Ring 0: Granit-Bruchstein). Das Original wieder setzen, nochmal schleichend schlagen: der Block wird wieder geweiht. Während der Wartung: Beten wird abgelehnt (Chat), ein Loch im Ring pausiert nur den Segen, die Ages bleiben. Nach 60 s endet die Wartung von selbst (Chat an alle, Käfig weg), stehende Blöcke werden wieder geweiht. Gegenprobe: schleichend mit Spitzhacke in der Hand oder ohne Schleichen schlagen schaltet nichts um.

**Jade:** Auf einen geweihten Block: „Consecrated …“, „Ring of the …“, „Original: …“ und „Unbreakable (maintenance mode at the heart)“ bzw. die Restzeit.

**Texturen v2:** Alle Gegenstands-Texturen von `firmages:` (16 Dateien) sind neu gezeichnet (Hearthstone bis Ultimate Singularity, auch Unfired Hearth Idol und Sky Disc). Achte auf Lesbarkeit im Inventar und darauf, dass keine lila-schwarze Fehltextur auftaucht.

**Animierte Texturen:** In EMI `firmages:` filtern: Arcane und Awakened Keystone, Humming Core, Data Matrix, Star Chart, Quantum Core und Ultimate Singularity bewegen sich; der Origin-Altar pulsiert oben. Die geweihten Blöcke stehen unter Operator Utilities im Kreativ-Menü (Lampe als leuchtende Lampe).

### Mod-Updates vom 2026-10-13 (Pack 0.6.1, firmages-core unverändert 0.6.1)

30 Mods sind aktualisiert (Liste: `dev/poc-results.md`, „Maintenance 2026-10-13“); der Server ist damit komplett geprüft, der Client noch nicht. Bitte die Prism-Instanz neu syncen und achten auf:

- **Start und Menüs:** Client startet ohne Absturz. MezzConfig ist jetzt die Datei `…-0.6.8-standalone`; das Mod-Konfig-Menü (Mods-Liste → Config) öffnet sich, EMI und JEI 19.64 zeigen Rezepte wie vorher.
- **Client-Mods:** Entity Culling (Mobs hinter Wänden flackern nicht), FramedBlocks (Rahmenblöcke setzen und texturieren), Sophisticated Backpacks (Rucksack öffnen, Upgrades einsetzen).
- **Stargate Journey 0.6.51:** Wer Zeit hat, wählt wie im Abschnitt 0.4.0 von einem selbst gebauten Gate 9-16-21-33-2-37 und kommt in The Origin an. Neu in der Mod ist ein Andromeda-Gate mit DHD (age_9).
- **Entfernt:** Die AdvancedAE Strength Card ist deaktiviert (sie rüstete nur die ebenfalls deaktivierte Quantum-Rüstung auf); in EMI darf sie nicht auftauchen.
- **More Mekanism Processing 6.8:** Neue Metalle (Antimon, Bor, Magnesium, Palladium, Thorium) erscheinen ab age_6, ohne Barren-zu-Staub-Zerkleinern.
