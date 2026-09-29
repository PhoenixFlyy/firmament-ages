@echo off
rem Firmament Ages: serves the pack at http://localhost:8080/pack.toml for the dev client and test server.
rem packwiz serve refreshes the index itself on every request. Stop with Ctrl+C. Run from anywhere; the script switches to the repo root.
cd /d "%~dp0.."
"%~dp0..\tools\packwiz.exe" serve
