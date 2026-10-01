# Shrine viewer

Renders the shrine rings from the mod data (mod/firmages-core/src/main/resources/data/firmages/...).

    python dev/shrine-viewer/render_shrine.py --build-html --data-root mod/firmages-core/src/main/resources/data/firmages

Writes out/*.png (isometric views, build plans, overview), shrine_data.json and viewer.html (the claude.ai artifact page). Outputs are not tracked.
