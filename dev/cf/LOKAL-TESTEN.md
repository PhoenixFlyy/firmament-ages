# Lokal testen mit der CurseForge-App

Stand 2026-10-13, Pack 0.7.1. Du testest genau das Zip, das später hochgeladen wird. Die CurseForge-App lädt die Mods selbst von CurseForge, nur die eigenen Dateien stecken im Zip.

## 1. Zip bauen (einmal pro Stand)

Im Repo-Hauptordner `D:\Minecraft\MinecraftModServer\FirmamentAges`:

```
tools\packwiz.exe refresh
python dev\cf\export_cf.py
```

Ergebnis: `dev\exports\FirmamentAges-0.7.1-curseforge-upload.zip` (etwa 7 MB, wegen der Titelbild-Diashow und der Questbuch-Bilder). Das Skript listet am Ende, welche Jars in `overrides\mods` liegen. Erwartet ist nur noch `firmages-core` (MekaTFC ist durch die eigene Osmium-Ader ersetzt, TFC Ore Tooltips gestrichen); nach der Freigabe des Mod-Projekts keins.

## 2. In der CurseForge-App importieren

1. CurseForge-App starten, links **Minecraft** wählen.
2. Oben **Create Custom Profile** (oder das Plus), dann **Import**.
3. Das Zip aus Schritt 1 auswählen. Die App liest `manifest.json`, legt das Profil „Firmament Ages“ an und lädt alle 195 Mods. Beim ersten Mal dauert das einige Minuten, etwa 500 MB.
4. Die App richtet Java 21 selbst ein. Falls sie nach einem Launcher fragt, den normalen Minecraft Launcher nehmen.
5. Im Profil **Settings** öffnen und den RAM auf 10 GB setzen (Standard sind 4 GB, zu wenig für 196 Mods).
6. **Play**. Der erste Start braucht wegen der Shader-Vorbereitung drei bis fünf Minuten.

Wenn der Import mit einer Fehlermeldung zu einer Mod-Datei abbricht: Mod-Name und Meldung notieren und mir schicken, dann ist eine CurseForge-Datei zurückgezogen worden.

## 3. Mit dem Test-Server verbinden

Der Test-Server liegt in `test-server\` und hat die Whitelist an. Zwei Fenster:

Fenster 1, Server starten (Java 21 aus `tools`, nicht das System-Java):

```
cd D:\Minecraft\MinecraftModServer\FirmamentAges
python dev\run_server.py --hold 0
```

Warten, bis in der Konsole `Done` steht. Dann in dieselbe Konsole eintippen:

```
whitelist add DEIN_MINECRAFT_NAME
op DEIN_MINECRAFT_NAME
```

Fenster 2: in der CurseForge-App das Profil spielen, **Multiplayer**, Server `localhost` hinzufügen, verbinden.

Alternativ geht auch **Singleplayer** im Profil. Dann fehlt nur der Mehrspieler-Teil der Zeremonie, alles andere läuft gleich.

## 4. Was du testest

Die Checkliste steht in `dev\client-test-brief.md`, oben der Nachtrag für 0.7.1 und darunter der Abschnitt für 0.7.0 in der Reihenfolge, in der Du ihn abarbeitest. **Für 0.7.0 und 0.7.1 ein neues Profil importieren** (nicht das alte aktualisieren): Default Options, das frühe Ladefenster und der Willkommensbildschirm zeigen sich nur beim ersten Start eines frischen Profils bzw. bei einem neuen Spieler in einer frischen Welt. Kurz:

- Neu in 0.7.0: Ladebildschirm und Titelbild, Standard-Tasten (Sprint Shift, Schleichen Strg, Age-Anzeige H), stiller Join, Willkommen, HUD-Age-Anzeige, kein EMI und kein Jade in Dawn, Questbuch im Firmament-Stil mit nur freigeschalteten Kapiteln, TFC ohne Durst und ohne Ernährungsstrafe.
- Neu in 0.7.1: EMI erst ab der Bronzezeit (Jade weiter ab der Steinzeit), Physics-Staff-Drehmodus auf Komma und Cataclysm-Brustplatte auf Apostroph (Ä) statt Tab und Y, MekaSuit-Module herstellbar, TFC-Stahlstufe und IE-Chunk-Loader versteckt.

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
