# Shrine viewer

Renders the shrine rings from the mod data (mod/firmages-core/src/main/resources/data/firmages/...).

    python dev/shrine-viewer/render_shrine.py --build-html --data-root mod/firmages-core/src/main/resources/data/firmages

Writes out/*.png (isometric views, build plans, overview), shrine_data.json and viewer.html (the claude.ai artifact page). Outputs are not tracked.

The cumulative renders, the overview and the viewer default to the consecrated look (SPEC §17: every accepted ring
is one sky-marble family with the Age's beam colour as a thin accent; roles from firmages_shrine/consecration.json).
`--as-built` draws them in the Age materials; ring_N.png and plan_after_age_N.png always show the build recipe,
plan_after_age_N_consecrated.png the roles. The viewer has an "As built / Consecrated" toggle.
out/shrine_cutout_age_8.png (transparent) feeds dev/cf/make_logo.py (logo, avatar, banner).
