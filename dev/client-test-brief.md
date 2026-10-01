# Client-Test: Age-Übergang am Schrein (firmages-core 0.3.0)

Ausführlich: `dev/dev-client.md`, Abschnitt „Age-Übergang testen“. Hier die Kurzfassung.

## Was Du tust

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

## Worauf Du achtest

- Sitzt die Geistervorschau genau um das Herz (nicht einen Block zu hoch/tief)?
- Zeremonie: Musik aus, Lichtstrahl (bernstein), Himmel orange, **genau ein** Titel „The Bronze Age dawns“, Chat „Caelum: A fire that keeps its shape. I see you.“, danach Segen „Hearthward“.
- Reload-Einfrieren nach ca. 3 s: Aktionsleiste „The world realigns...“, Effekte laufen weiter, kein Kick.
- EMI ohne Relog: `create:andesite_alloy` hat jetzt ein Rezept. K-Screen: Ziel-Quest „Offer the Hearthstone at the Shrine“ erledigt (sobald alle Keystones fertig sind).
- Hearthstone schwebt sichtbar über dem Sockel und lässt sich nicht abbauen. Stamm weg: „The shrine is broken...“, Age bleibt.

## Was Du meldest

- Pro Punkt oben: ja/nein, ungefähre Zeit, Auffälliges (gern Screenshot).
- Reload-Dauer aus `/firmages ages` und ob EMI danach ruckelte.
- Fehler im Chat; bei Problemen `test-server\logs\latest.log`, bei Client-Absturz den Crash-Report aus der Prism-Instanz.

## Seit dem Schreiben geändert (Stand 2026-10-01, firmages-core 0.3.3)

Die Schritte oben gelten unverändert; Ring 0, Hearthstone, Zeremonie und Segen „Hearthward“ sind gleich geblieben, nur heißt die Mod-Version jetzt 0.3.3 statt 0.3.0. Der Reload dauert auf dem Test-Server jetzt etwa 6,2 bis 6,6 s statt gut 8 s (2 Test-Spieler, alle Ages; der erste Reload nach dem Start bis etwa 8 bis 10 s). Bei Age-Vergaben, die auch Mob- oder Helfer-Stages ändern (gemessen bei age_4 und age_6), friert der Server zusätzlich etwa 2,6 s ein, bevor der Reload beginnt; dann endet der Reload erst um 12 s nach Zeremonie-Beginn, also knapp nach der 12-s-Zeremonie. Melde deshalb bitte auch, ob das Einfrieren gleich nach dem Titel kommt und ob „The world realigns...“ bis zum Ende sichtbar bleibt. Neu sind die Ringe 3 bis 8 (Spirit Circle bis Quantum Ring, 11x11 bis 21x21) mit eigenen Riten und Segen sowie die Signatur-Items Humming Core, Data Matrix, Star Chart und Quantum Core. Wenn Du Zeit hast: `/firmages shrine status` und die Geistervorschau eines späten Rings ansehen und den Tooltip „Signature item of the … Age“ an einem Signatur-Item prüfen (die neuen Items haben noch keine Textur).

## Neu ab firmages-core 0.4.0: The Origin, Endboss, Reaktor (Stand 2026-10-01)

Über den Schrein hinaus kannst Du jetzt drei Dinge im Client testen, am besten zu zweit und in Creative-Vorbereitung. **The Origin:** `/stage grant @s age_9`, dann `/firmages origin tp` (ohne age_9 muss „This dimension is locked!“ kommen). Achte auf End-Himmel, dunkles Licht, die schwebende Scheibe mit Altar, neun Inseln und das Rück-Stargate mit DHD; vom DHD aus nach Hause wählen und prüfen, ob Du heil ankommst. Wer Zeit hat, baut in der Overworld ein Classic Stargate (`/give` die Teile) und wählt 9-16-21-33-2-37 plus Ursprungssymbol (steht auch im Tooltip von „Coordinates of The Origin“). **Endboss:** Alle Online-Spieler (nicht Zuschauer) stellen sich höchstens 6 Blöcke um den Altar. Dann öffnet sich das Gateway mit drei Wellen (Maledictus, Ignis, beide), danach erscheint „The Primordial“ mit Phasen bei 2/3 und 1/3 Leben. Melde, wie lange der Kampf mit Chaotic-Ausrüstung dauert (auf dem Server zog ein 500-Schaden-Treffer nur etwa 8 Leben ab; er ist evtl. zu zäh), ob die FINALE-Zeremonie (Titel „Beyond the Firmament“, Stimme, Partikel, Himmel, Blitze) genau einmal läuft und ob im K-Screen das Finale abgehakt ist. `finale_won` lässt sich nicht zurücknehmen; `/firmages origin reset` startet nur den Kampf neu, also bitte in einer Testwelt. **Reaktor:** Einen Draconic Reactor in Creative bauen, den Reactor Controller an einen Stabilisator setzen, eine Truhe mit erweckten Draconium-Blöcken daneben und Redstone-Draht daran. Der Controller füllt den kalten Reaktor auf und meldet READY mit Redstone 15; laden und starten musst Du selbst. Im Betrieb muss er bei 80 % Umwandlung herunterfahren, nach dem Abkühlen Chaos-Splitter herausnehmen, Brennstoff nachfüllen und wieder READY melden, aber **nie** selbst starten. Status: leere Hand am Controller oder `/firmages reactor status`. Teste auch Rohre an Stabilisatoren (Brennstoff rein, Chaos raus, nur im kalten Zustand).
