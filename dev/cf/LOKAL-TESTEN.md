# Lokal testen mit der CurseForge-App

Stand 2026-10-13, Pack 0.8.0. Du testest genau das Zip, das später hochgeladen wird. Die CurseForge-App lädt die Mods selbst von CurseForge, nur die eigenen Dateien stecken im Zip.

## 1. Zip bauen (einmal pro Stand)

Im Repo-Hauptordner `D:\Minecraft\MinecraftModServer\FirmamentAges`:

```
tools\packwiz.exe refresh
python dev\cf\export_cf.py
```

Ergebnis: `dev\exports\FirmamentAges-0.8.0-curseforge-upload.zip` (etwa 7 MB, wegen der Titelbild-Diashow und der Questbuch-Bilder). Das Skript listet am Ende, welche Jars in `overrides\mods` liegen. Erwartet ist nur noch `firmages-core` (MekaTFC ist durch die eigene Osmium-Ader ersetzt, TFC Ore Tooltips gestrichen); nach der Freigabe des Mod-Projekts keins.

## 2. In der CurseForge-App importieren

Für 0.8.0 das Profil **neu importieren**, ein altes Profil (0.7.x) weder aktualisieren noch weiter nutzen: Es braucht das neue `firmages-core-0.8.0.jar` (Netzwerkprotokoll 2, der Server weist einen 0.7.x-Client ab), die neuen Default Options greifen nur beim ersten Start eines frischen Profils, und nur dann starten die Shader (Complementary Reimagined) eingeschaltet.

1. CurseForge-App starten, links **Minecraft** wählen.
2. Oben **Create Custom Profile** (oder das Plus), dann **Import**.
3. Das Zip aus Schritt 1 auswählen. Die App liest `manifest.json`, legt das Profil „Firmament Ages“ an und lädt alle 206 Mods und die zwei Shaderpacks. Beim ersten Mal dauert das einige Minuten, etwa 500 MB.
4. Die App richtet Java 21 selbst ein. Falls sie nach einem Launcher fragt, den normalen Minecraft Launcher nehmen.
5. Im Profil **Settings** öffnen und den RAM auf 10 GB setzen (Standard sind 4 GB, zu wenig für 207 Mods).
6. **Play**. Der erste Start braucht wegen der Shader-Vorbereitung drei bis fünf Minuten.

Wenn der Import mit einer Fehlermeldung zu einer Mod-Datei abbricht: Mod-Name und Meldung notieren und mir schicken, dann ist eine CurseForge-Datei zurückgezogen worden.

## 3. Mit dem Test-Server verbinden

Der Test-Server liegt in `test-server\`. Er läuft zurzeit **ohne Whitelist** (`white-list=false` in `test-server\server.properties`), weil nur Du testest; `whitelist add` ist nicht nötig. Steht dort noch `white-list=true`, nach dem Start einmal `whitelist off` in die Server-Konsole tippen (das schreibt `white-list=false`). Zwei Fenster:

Fenster 1, Server starten (Java 21 aus `tools`, nicht das System-Java). Für eine frische Dawn-Welt vorher den Ordner `test-server\world` löschen (Explorer oder PowerShell `Remove-Item -Recurse -Force test-server\world`). Dann:

```
cd D:\Minecraft\MinecraftModServer\FirmamentAges
powershell -NoProfile -ExecutionPolicy Bypass -File dev\start_test_server.ps1
```

Der Server läuft im Vordergrund, bis Du `stop` eintippst; die Konsole nimmt Befehle direkt an. `python dev\run_server.py` ist nur für automatische Läufe da: `--hold 0` schickt gleich nach `Done` ein `stop`, und `--timeout` beendet die JVM.

Warten, bis in der Konsole `Done` steht. Fenster 2: in der CurseForge-App das Profil spielen, **Multiplayer**, Server `localhost` hinzufügen, verbinden. Zuerst den Dawn-Join prüfen (`dev\client-test-brief.md`: stiller Join, Willkommen, kein EMI und kein Jade), **erst danach** in die Server-Konsole:

```
op DEIN_MINECRAFT_NAME
```

Vorher kein `op`, sonst stehen Op-Meldungen im Chat und verfälschen den stillen Join.

Alternativ geht auch **Singleplayer** im Profil. Dann fehlt nur der Mehrspieler-Teil der Zeremonie, alles andere läuft gleich.

## 4. Was du testest

Die Checkliste steht in `dev\client-test-brief.md`, oben die Nachträge für 0.8.0, 0.7.2 und 0.7.1 und darunter der Abschnitt für 0.7.0 in der Reihenfolge, in der Du ihn abarbeitest. **Für 0.7.0 bis 0.8.0 ein neues Profil importieren** (nicht das alte aktualisieren): Default Options (auch die Shader-Voreinstellung), das frühe Ladefenster und der Willkommensbildschirm zeigen sich nur beim ersten Start eines frischen Profils bzw. bei einem neuen Spieler in einer frischen Welt. Kurz:

- Neu in 0.7.0: Ladebildschirm und Titelbild, Standard-Tasten (Sprint Shift, Schleichen Strg, Age-Anzeige H), stiller Join, Willkommen, HUD-Age-Anzeige, kein EMI und kein Jade in Dawn, Questbuch im Firmament-Stil mit nur freigeschalteten Kapiteln, TFC ohne Durst und ohne Ernährungsstrafe.
- Neu in 0.7.1: EMI erst ab der Bronzezeit (Jade weiter ab der Steinzeit), Physics-Staff-Drehmodus auf Komma und Cataclysm-Brustplatte auf Apostroph (Ä) statt Tab und Y, MekaSuit-Module herstellbar, TFC-Stahlstufe und IE-Chunk-Loader versteckt.
- Neu in 0.7.2: Titel- und Pausemenü repariert (nur noch die gewollten Knöpfe, Versionszeile lesbar), TFC-Reiter im Inventar nach Age (Kalender ab Steinzeit, Klima ab Eisenzeit, Ernährung und Field Guide nie), Simple Voice Chat entfernt, Mystical-Agriculture-Erze als TFC-Adern ab der Informationszeit, engere Absätze in der Quest-Ansicht.
- Neu in 0.8.0: Shader standardmäßig an (Complementary, MakeUp als leichte Alternative, O-Menü), Enhanced Celestials 2, Leistungs-Mods, Titelbild „Constellation“, Questbuch als Fenster (75 %), Betten ab der Eisenzeit, Fackeln 36 Minuten, echte Dunkelheit, keine Koordinaten vor der Bronzezeit, Blutmond ab der Eisenzeit, Cave Dweller bis zur Eisenzeit, Jade ab der Bronzezeit, Ping Wheel, Crash Assistant und TFCGenViewer entfernt.

Aus den früheren Phasen, weiter gültig:

- Der Age-Übergang am Schrein: bauen, opfern, beten, Zeremonie, danach die Weihe der Ringblöcke.
- Wartungsmodus: schleichend mit leerer Hand auf das Herz schlagen, Block abbauen, Original fällt, Block zurücksetzen.
- Texturen und Animationen der Signatur-Items, Jade-Tooltips am Schrein.
- Ein Sigil benutzen und die Gateway-Wellen schlagen.
- EMI: nach jeder Freischaltung erscheinen die Rezepte der neuen Age, vorher nicht.
- Reload-Freeze bei der Freischaltung: wie lange, und bricht die Verbindung ab?

Schnellweg zum Ausprobieren später Ages, als Operator:

```
/firmages ages
/stage grant @s age_4
```

Das Pack hält den Server-Zustand auf dem höchsten vergebenen Age. Zurück auf Dawn geht nur mit `/firmages ages simulate revoke`, wenn in `test-server\config\firmages-server.toml` `debug.allowSimulate = true` steht.

## 5. Was du meldest

- Pro Punkt: ja oder nein, bei Nein die Chat-Meldung oder einen Screenshot.
- Bei einem Absturz: `test-server\logs\latest.log` und den Crash-Report aus dem CurseForge-Profil (`Instances\Firmament Ages\crash-reports`).
- Reload-Dauer aus `/firmages ages`.

## 6. Nach dem Test: Veröffentlichung

Die Upload-Schritte stehen in `dev\cf\UPLOAD-ANLEITUNG.md`. Offene Entscheidungen gibt es keine mehr: MekaTFC ist durch die eigene Osmium-Ader ersetzt, TFC Ore Tooltips gestrichen (`dev\curseforge-readiness.md`).
