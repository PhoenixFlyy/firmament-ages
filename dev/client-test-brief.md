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
