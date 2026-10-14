# Stilregeln für Spielertext

Gilt für alles, was ein Spieler liest: Questbuch (`dev/gen_quests.py`), firmages-core-Lang, KubeJS-Tooltips,
Stage-Dateien, ProgressiveStages-Meldungen, Titelbild, CurseForge-Seite. Spielertext ist Englisch, diese Regeln
sind Deutsch. Prüfung: `python dev/validate_quests.py --no-jars --style` (muss 0 Warnungen melden).

## Stimme

Knapp, alt, klar. Bilder aus Himmel, Feuer, Stein und Erde. Der Satz des Packs: "The sky remembers."
Caelum ist der Himmel selbst ("Caelum, the Firmament: the sky itself"), kein Gott über dem Himmel. Caelum spricht nur
in Zeremonien (die erste beim First Spark, noch ohne Schrein) und am Schrein. Die Spieler sind "the fellowship" oder "all of you", nie "the team" im Text (FTB-Menüs bleiben, wie sie
sind; auf der CurseForge-Seite heißt es "one team per server", weil FTB Teams so heißt).

Gut:   "Fire is tamed. Clay is yours now."
Gut:   "Veins of a later Age sleep as plain stone."
Nicht: "Embark on an epic journey through ten unique Ages!"

## Questbeschreibung

1. Erster Satz: was zu tun ist, im Imperativ. Zweiter Satz: wozu (was es braucht, was es öffnet).
   Gut:   "Build a crusher and feed it ore pieces. It returns twice the metal as powder and dust."
   Nicht: "The IE crusher grinds TFC ore pieces into mineral powder."
2. Höchstens drei Absätze. Wortgrenzen: Schritt und Nebenquest 25, Keystone, Hinweis (Zahnrad) und Ring 40,
   Eingang, Ziel und Welcome-Quest 70. Knapping-Raster (Verbatim-Zeilen aus `X` und `-`) zählen nicht, die Prosa
   drumherum schon; ein Absatz mit Raster und Überschrift zählt nicht als Absatz.
   Ausnahme Lehrquest (bis 110 Wörter): die eine Stelle, an der eine Mechanik oder ein Bauplan erklärt wird, den kein
   Rezeptbetrachter zeigt. Die Liste steht in `STYLE_TEACHING` in `dev/validate_quests.py` (Knapping-Anleitung,
   Knapping-Muster, Herdkreis, Keystone-Leihe, Dreschmaschine, Feuer und Fackeln vor der ersten Nacht, Ton-Muster,
   Steinzeit-Heim, Werkbank, Nether-Opfer, Dawn-Regeln, Grubenofen, Kohlenmeiler, Gießen in die Form,
   Holzkohleschmiede), jede mit Grund. Neue Einträge nur mit Grund.
3. Kein Rezept, das der Rezeptbetrachter zeigt. Ausnahmen: Dawn und Steinzeit (EMI erst ab der Bronzezeit,
   `ui.emiFromAge = age_1`) und die Opfergaben (ihre Zutaten sind die Checkliste der Stränge).
4. Jede Mechanik wird an genau einer Stelle erklärt (Geistervorschau am Herz: "Raise the Shrine"; Siegel: "When a
   Boss Will Not Come"; Gebet: Ziel der Steinzeit; Leihe: "Awakened Keystone"). Sonst ein Halbsatz: "the heart shows
   what is missing", "The Wither Sigil opens the Wither Trial instead."
5. Gleich gebaute Quests klingen gleich: alle Ziele "Craft ... / Raise ...; the heart shows what is missing. Lay ...
   and pray. / Caelum keeps ... and opens ...". Eingänge heißen "The X Age Dawns" wie der Zeremonie-Titel.

## Namen und Schreibweisen

- Kapiteltitel: der Name der Age ("Bronze Age"), ohne Nummer: das Pack sagt "Ten Ages" (2026-10-14, ersetzt
  r3). Im Fließtext: "the Bronze Age". "Age" mit Ziffer nirgends, auch nicht in Titeln (Lint).
- Prolog: "Dawn" ohne Artikel.
- Gegenstände und Maschinen heißen wie im Spiel ("Aluminium Plate", "Engineer's Hammer"), kleingeschrieben im
  Satz, wenn das Spiel sie nicht als Eigennamen führt ("a steel casing"). Eigene Namen groß: Shrine Heart,
  Offering Plinth, Hearth Circle, Hearth Idol, Arcane Keystone, Frontier Sigil, The Origin, The Gathering.
- Glossar (immer dieselben Wörter): the Chronicle (Questbuch, überall so: Willkommen, Quests, Doku, CurseForge), shrine, heart, plinth, ring, rite, offering, relic,
  strand, keystone, goal, sigil, trial, fellowship. Nicht: signature item, Gateway, stand-in, quest.
- Bedienung: "right-click", "sneak and right-click", "hold right-click", "punch" (linke Taste); nie "use" für
  die Maustaste (2026-10-14: Spieler kennen "right-click", "hold use" ist Fachjargon). Tasten mit Standard
  nennen: "Press K", "Hold H".
- Rechtschreibung: amerikanisch wie Minecraft (armor, color), außer ein Spielname schreibt anders (Aluminium).
- Zahlen: Mengen bis zwölf als Wort ("four sheets"), ab 13 als Ziffer ("32 plates"), Maße als Ziffer ("21 by 21").

## Verboten

- Ausrufezeichen, Gedankenstriche (— –), Auslassungspunkte, Emojis.
- Modnamen (TFC, Create, Mekanism, AE2, IE, Occultism, Theurgy, Ars ...), außer sie stecken im Gegenstandsnamen
  ("MEGA Cell", "draconic pickaxe") oder sind ein Ort (Twilight Forest, The End).
- Meta-Wörter: quest, stage, unlock, lock, EMI, JEI, tooltip, "in this pack", "mod". Statt "unlock": "open", "wake",
  "can be made".
- Füllwörter und KI-Muster: journey, embark, dive into, unleash, unlock the power, realm, vast, mighty, powerful,
  epic, amazing, incredible, seamless, crucial, essential, "it's important to note", "make sure", "don't forget",
  "allows you to", drei Adjektive in Folge. "endless" nur als Mengenwort ("the first endless ore"), "ultimate" nur im
  Namen ("Ultimate Singularity", "ultimate tier").
- Wortspiele mit Ausrufezeichen als Titel ("Do a Barrel Roll!"). Titel sind kurz, Substantiv, Title Case, nie gleich
  einem Age-Namen.

## Prüfen

- `python dev/validate_quests.py --no-jars --style`: Wortgrenzen pro Questart, mehr als drei Absätze, verbotene
  Wörter, Meta-Wörter, Modnamen, "Age <Zahl>" in Titel oder Text, "!" überall, Gedankenstriche, Auslassungspunkte.
  Die Muster stehen in `STYLE_BANNED`, `STYLE_META` und `STYLE_MODS`; Treffer in Titeln, Untertiteln, Aufgabentiteln
  und Beschreibungen.
- Nach jeder Textänderung: `python dev/gen_quests.py`, dann `git diff config/ftbquests/quests/lang/en_us.snbt`
  lesen. Nur Lang-Zeilen dürfen sich ändern, wenn nur Text geändert wurde.
- Texte außerhalb des Questbuchs (Lang, KubeJS, Stage-Dateien, CurseForge) prüft das Skript nicht: dieselben Regeln
  von Hand.
