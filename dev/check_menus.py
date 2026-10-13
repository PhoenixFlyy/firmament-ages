#!/usr/bin/env python3
"""Firmament Ages - offline check of the FancyMenu title, pause and loading screens and of the shader defaults.

Static proof for config/fancymenu/ (FancyMenu 3.9.14, Drippy 3.1.5, MC 1.21.1, NeoForge 21.1.252):
  menus      customizablemenus.txt enables TitleScreen, PauseScreen and Drippy's DrippyOverlayScreen
  ids        every vanilla_button id in a layout is one FancyMenu 3.9.14 actually assigns to a widget of that screen
             on 1.21.1 (KNOWN below, read with javap from the jar); ids FancyMenu knows but can never match on 1.21.1
             (DEAD below) are an error, so is hiding the copyright line (FancyMenu forces it visible)
  hides      title: Realms, Mods, logo, splash, branding, Realms icons hidden in the layout; Language and
             Accessibility hidden by firmages-core (MenuButtons keys, FancyMenu cannot address them); Mods and
             Advancements also by firmages-core. Pause: Advancements, Statistics, Feedback, Report Bugs (both
             feedback variants), Server Links, Open to LAN / Player Reporting hidden; Mods by firmages-core.
             Loading overlay: Mojang logo and vanilla progress bar hidden, nothing else is a vanilla widget there
  shown      title keeps exactly Singleplayer, Multiplayer, Options, Quit; pause exactly Back to Game, Options,
             Disconnect (restyled, explicitly placed)
  create     config/create-client.toml sets mainMenuConfigButtonRow and ingameMenuConfigButtonRow to 0
  version    the title version line reads "Firmament Ages <pack.toml version>", sits bottom-left, its box holds
             the text (scroll border + text_border + glyph + shadow), and at 1920x1080 and 2560x1440 with GUI
             scale 2, 3 and auto it stays on screen and clear of the buttons, the wordmark and the copyright line;
             the pause buttons stay on screen and below the "Game Menu" title
  shaders    config/defaultoptions/extra/config/iris.properties turns shaders on with a shaderPack that has a metafile
             in shaderpacks/ (same filename); config/irisblockcompat-common.toml disables shaders in firmages:origin
             and in every Ad Astra dimension (data/ad_astra/dimension/*.json of the Ad Astra jar in the CurseForge
             instance, or --adastra-jar) and keeps its waving list empty (Complementary maps that id to light sources)

Usage: python dev/check_menus.py [--jar PATH_TO_FANCYMENU_JAR] [--adastra-jar PATH_TO_AD_ASTRA_JAR]
With --jar (or the jar found in the CurseForge instance), every KNOWN id is also searched in the jar's classes.
Exit code 0 = all checks pass.
"""
import argparse
import math
import re
import sys
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FM = ROOT / "config" / "fancymenu"
LAYOUTS = FM / "customization"
MENU_BUTTONS = ROOT / "mod" / "firmages-core" / "src" / "main" / "java" / "dev" / "firmages" / "core" / "client" / "immersion" / "MenuButtons.java"
DEFAULT_JAR = Path("C:/Users/Felix/curseforge/minecraft/Instances/Firmament Ages/mods/fancymenu_neoforge_3.9.14_MC_1.21.1.jar")
INSTANCE_MODS = DEFAULT_JAR.parent
IRIS_PROPERTIES = ROOT / "config" / "defaultoptions" / "extra" / "config" / "iris.properties"
BLOCK_COMPAT = ROOT / "config" / "irisblockcompat-common.toml"
ORIGIN_DIM = "firmages:origin"

# Ids FancyMenu 3.9.14 assigns on MC 1.21.1 [javap]. Title: TitleScreenWidgetIdentificationContext (translation key of
# the widget label) and MixinTitleScreen (RendererWidget ids, copyright PlainTextButton). Pause: MixinPauseScreen sets
# a UniqueWidget id on every vanilla button, and WidgetIdentifierHandler.getUniversalIdentifierForWidgetMeta returns
# that id before asking PauseScreenWidgetIdentificationContext, so only the pause_* ids match. Drippy:
# DrippyOverlayScreen.buildLogoWidget/buildProgressBarWidget.
KNOWN = {
    "title_screen": {
        "mc_titlescreen_singleplayer_button", "mc_titlescreen_multiplayer_button", "mc_titlescreen_options_button",
        "mc_titlescreen_quit_button", "mc_titlescreen_realms_button", "mc_titlescreen_playdemo_button",
        "forge_titlescreen_mods_button", "minecraft_logo_widget", "minecraft_splash_widget",
        "minecraft_branding_widget", "minecraft_realms_notification_icons_widget", "title_screen_copyright_button",
    },
    "pause_screen": {
        "pause_title_widget", "pause_return_to_game_button", "pause_advancements_button", "pause_stats_button",
        "pause_send_feedback_button", "pause_report_bugs_button", "pause_feedback_button",
        "pause_server_links_button", "pause_options_button", "pause_share_to_lan_button", "pause_disconnect_button",
    },
    "drippy_loading_overlay": {"mojang_logo", "progress_bar"},
}
# Known to FancyMenu but never matched on 1.21.1 (reason in the message).
DEAD = {
    "mc_titlescreen_language_button": "FancyMenu maps narrator.button.language, 1.21.1 labels it options.language",
    "mc_titlescreen_accessibility_button": "FancyMenu maps narrator.button.accessibility, 1.21.1 labels it options.accessibility",
    "title_screen_copyright_button": "FancyMenu never hides the copyright line (VanillaWidgetElement.isHidden)",
}
for _pid in ("return_to_game", "advancements", "stats", "feedback", "report_bugs", "options", "lan", "disconnect"):
    DEAD[f"mc_pausescreen_{_pid}_button"] = "shadowed by the pause_* UniqueWidget id MixinPauseScreen sets"

HIDE_LAYOUT = {
    "title_screen": {"mc_titlescreen_realms_button", "forge_titlescreen_mods_button", "minecraft_logo_widget",
                     "minecraft_splash_widget", "minecraft_branding_widget",
                     "minecraft_realms_notification_icons_widget"},
    "pause_screen": {"pause_advancements_button", "pause_stats_button", "pause_send_feedback_button",
                     "pause_report_bugs_button", "pause_feedback_button", "pause_server_links_button",
                     "pause_share_to_lan_button"},
    "drippy_loading_overlay": {"mojang_logo", "progress_bar"},
}
SHOWN = {
    "title_screen": {"mc_titlescreen_singleplayer_button", "mc_titlescreen_multiplayer_button",
                     "mc_titlescreen_options_button", "mc_titlescreen_quit_button"},
    "pause_screen": {"pause_return_to_game_button", "pause_options_button", "pause_disconnect_button"},
    "drippy_loading_overlay": set(),
}
# Translation keys firmages-core MenuButtons must hide (constant name -> key).
CORE_KEYS = {"MODS": "fml.menu.mods", "ADVANCEMENTS": "gui.advancements",
             "LANGUAGE": "options.language", "ACCESSIBILITY": "options.accessibility"}

SCREENS = [(1920, 1080), (2560, 1440)]
GUI_SCALES = [2, 3, 0]  # 0 = auto
FONT_LINE = 9
GLYPH_MAX_W = 6  # widest default-font glyph incl. spacing (W, M, @ are 6)
COPYRIGHT = "Copyright Mojang AB. Do not distribute!"


def parse_layout(path):
    """Sections of a FancyMenu layout as (type, {key: value})."""
    sections, cur, name = [], None, None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("type ="):
            continue
        if line.endswith("{"):
            name, cur = line[:-1].strip(), {}
        elif line == "}":
            sections.append((name, cur))
            cur = None
        elif cur is not None and "=" in line:
            k, v = line.split("=", 1)
            cur[k.strip()] = v.strip()
    return sections


def auto_scale(w, h):
    """Window.calculateScale(0, false) of MC 1.21.1."""
    i = 1
    while i < w and i < h and w // (i + 1) >= 320 and h // (i + 1) >= 240:
        i += 1
    return i


def scaled(w, h, s):
    return math.ceil(w / s), math.ceil(h / s)


def rect(el, sw, sh):
    """Absolute rect (x, y, w, h) of a non-sticky element (ElementAnchorPoint.getElementPosition*)."""
    a, x, y = el["anchor_point"], int(el["x"]), int(el["y"])
    w, h = int(el["width"]), int(el["height"])
    ox = {"left": 0, "centered": sw // 2, "right": sw}[a.split("-")[1]]
    oy = {"top": 0, "mid": sh // 2, "bottom": sh}[a.split("-")[0]]
    return ox + x, oy + y, w, h


def overlap(a, b):
    return a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]


def check_shaders(adastra_jar, errors, notes):
    """Shaders on by default (Iris), off in the Origin and in every Ad Astra dimension (Iris Block Compat)."""
    if not IRIS_PROPERTIES.exists():
        errors.append(f"{IRIS_PROPERTIES.relative_to(ROOT).as_posix()} missing (Default Options copies it once)")
    else:
        props = dict(line.split("=", 1) for line in IRIS_PROPERTIES.read_text(encoding="utf-8").splitlines()
                     if "=" in line and not line.startswith("#"))
        if props.get("enableShaders") != "true":
            errors.append("iris.properties: enableShaders is not true")
        pack = props.get("shaderPack", "")
        metas = {tomllib.loads(f.read_text(encoding="utf-8"))["filename"]: f
                 for f in (ROOT / "shaderpacks").glob("*.pw.toml")}
        if pack not in metas:
            errors.append(f"iris.properties: shaderPack '{pack}' has no metafile in shaderpacks/ ({sorted(metas)})")
        else:
            notes.append(f"default shader pack {pack} -> shaderpacks/{metas[pack].name}")
    if not BLOCK_COMPAT.exists():
        errors.append(f"{BLOCK_COMPAT.relative_to(ROOT).as_posix()} missing")
        return
    cfg = tomllib.loads(BLOCK_COMPAT.read_text(encoding="utf-8"))
    disabled = set(cfg.get("disabledDimensions", []))
    if cfg.get("wavingBlocks") != []:
        errors.append("irisblockcompat-common.toml: wavingBlocks must stay [] (id 10031 glows under Complementary)")
    if ORIGIN_DIM not in disabled:
        errors.append(f"irisblockcompat-common.toml: {ORIGIN_DIM} not in disabledDimensions")
    jar = adastra_jar or next(iter(sorted(INSTANCE_MODS.glob("adastra-*-neoforge.jar"))), None)
    if not jar or not jar.exists():
        errors.append("Ad Astra jar not found: dimensions not checked (pass --adastra-jar)")
        return
    with zipfile.ZipFile(jar) as z:
        dims = sorted("ad_astra:" + n[len("data/ad_astra/dimension/"):-len(".json")] for n in z.namelist()
                      if re.fullmatch(r"data/ad_astra/dimension/[a-z0-9_]+\.json", n))
    if not dims:
        errors.append(f"{jar.name}: no data/ad_astra/dimension/*.json found")
    for d in dims:
        if d not in disabled:
            errors.append(f"irisblockcompat-common.toml: Ad Astra dimension {d} not in disabledDimensions")
    notes.append(f"shaders off in {ORIGIN_DIM} and the {len(dims)} Ad Astra dimensions of {jar.name}")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--jar", type=Path, default=None, help="FancyMenu 3.9.14 jar to cross-check the KNOWN ids")
    ap.add_argument("--adastra-jar", type=Path, default=None, help="Ad Astra jar whose dimensions need shaders off")
    args = ap.parse_args()
    errors, notes = [], []
    check_shaders(args.adastra_jar, errors, notes)

    # menus
    cm = (FM / "customizablemenus.txt").read_text(encoding="utf-8")
    for cls in ("net.minecraft.client.gui.screens.TitleScreen", "net.minecraft.client.gui.screens.PauseScreen",
                "de.keksuccino.drippyloadingscreen.customization.DrippyOverlayScreen"):
        if not re.search(r"^" + re.escape(cls) + r" \{$", cm, re.M):
            errors.append(f"customizablemenus.txt: {cls} not enabled")

    layouts = {}
    for f in sorted(LAYOUTS.glob("*.txt")):
        secs = parse_layout(f)
        meta = next((d for n, d in secs if n == "layout-meta"), {})
        layouts[meta.get("identifier", f.stem)] = (f, secs)
    for screen in KNOWN:
        if screen not in layouts:
            errors.append(f"no layout for {screen}")
    # ids, hides, shown
    for screen, (f, secs) in layouts.items():
        vb = [d for n, d in secs if n == "vanilla_button"]
        ids = [d.get("instance_identifier", "") for d in vb]
        dup = {i for i in ids if ids.count(i) > 1}
        if dup:
            errors.append(f"{f.name}: duplicate vanilla_button ids {sorted(dup)}")
        hidden = {d["instance_identifier"] for d in vb if d.get("is_hidden") == "true"}
        shown = {d["instance_identifier"] for d in vb if d.get("is_hidden") != "true"}
        for i in ids:
            if i in DEAD:
                errors.append(f"{f.name}: {i} has no effect on 1.21.1 ({DEAD[i]})")
            elif i not in KNOWN.get(screen, set()):
                errors.append(f"{f.name}: {i} is not a FancyMenu 3.9.14 id of {screen}")
        if screen in HIDE_LAYOUT:
            for i in sorted(HIDE_LAYOUT[screen] - hidden):
                errors.append(f"{f.name}: {i} must be hidden")
            if shown != SHOWN[screen]:
                errors.append(f"{f.name}: restyled buttons {sorted(shown)}, expected {sorted(SHOWN[screen])}")
            for d in vb:
                if d.get("is_hidden") != "true" and not all(k in d for k in ("anchor_point", "x", "y", "width", "height")):
                    errors.append(f"{f.name}: {d['instance_identifier']} has no explicit position and size")

    # firmages-core part of the hide list
    src = MENU_BUTTONS.read_text(encoding="utf-8")
    for const, key in CORE_KEYS.items():
        if not re.search(rf'static final String {const} = "{re.escape(key)}";', src):
            errors.append(f"MenuButtons.java: constant {const} = \"{key}\" missing")
    if not re.search(r"hide\(find\(widgets, LANGUAGE\)\)", src) or not re.search(r"hide\(find\(widgets, ACCESSIBILITY\)\)", src):
        errors.append("MenuButtons.java: Language/Accessibility are not hidden on the title screen")

    # create
    cc = ROOT / "config" / "create-client.toml"
    text = cc.read_text(encoding="utf-8") if cc.exists() else ""
    for key in ("mainMenuConfigButtonRow", "ingameMenuConfigButtonRow"):
        if not re.search(rf"^\s*{key}\s*=\s*0\s*$", text, re.M):
            errors.append(f"config/create-client.toml: {key} = 0 missing")

    # version line and geometry
    pack_version = re.search(r'^version = "([^"]+)"', (ROOT / "pack.toml").read_text(encoding="utf-8"), re.M)[1]
    _, tsecs = layouts.get("title_screen", (None, []))
    elements = {d.get("instance_identifier"): d for n, d in tsecs if n in ("element", "vanilla_button")}
    ver = elements.get("fa_title_version")
    if not ver:
        errors.append("title layout: fa_title_version missing")
    else:
        if ver.get("source") != f"Firmament Ages {pack_version}":
            errors.append(f"title layout: version line '{ver.get('source')}' != 'Firmament Ages {pack_version}' (pack.toml)")
        if ver.get("anchor_point") != "bottom-left":
            errors.append("title layout: version line is not anchored bottom-left")
        if ver.get("scale", "1.0") not in ("1", "1.0"):
            errors.append("title layout: version line scale differs from the other elements (1.0)")
        border = int(ver.get("text_border", "2"))
        need_h = 2 * 1 + 2 * border + FONT_LINE + 1  # scroll-area border, text border, glyph line, shadow
        if int(ver["height"]) < need_h:
            errors.append(f"title layout: version box {ver['height']} px high, text needs {need_h}")
        need_w = 2 * 1 + 2 * border + GLYPH_MAX_W * len(ver.get("source", ""))
        if int(ver["width"]) < need_w:
            errors.append(f"title layout: version box {ver['width']} px wide, text needs up to {need_w}")
        word = elements.get("fa_title_wordmark")
        buttons = [elements[i] for i in sorted(SHOWN["title_screen"]) if i in elements]
        pause = {d["instance_identifier"]: d for n, d in layouts.get("pause_screen", (None, []))[1]
                 if n == "vanilla_button" and d.get("is_hidden") != "true" and "anchor_point" in d}
        for fw, fh in SCREENS:
            for gs in GUI_SCALES:
                s = gs or auto_scale(fw, fh)
                sw, sh = scaled(fw, fh, s)
                tag = f"{fw}x{fh} GUI {gs or 'auto'}={s} ({sw}x{sh})"
                vr = rect(ver, sw, sh)
                text_r = (vr[0] + 1 + border, vr[1] + 1 + border, len(ver["source"]) * GLYPH_MAX_W, FONT_LINE + 1)
                if vr[0] < 2 or vr[1] < 0 or vr[0] + vr[2] > sw or vr[1] + vr[3] > sh - 2:
                    errors.append(f"{tag}: version box {vr} not inside the screen with a 2 px inset")
                for b in buttons + ([word] if word else []):
                    if overlap(text_r, rect(b, sw, sh)):
                        errors.append(f"{tag}: version text {text_r} overlaps {b['instance_identifier']} {rect(b, sw, sh)}")
                for b in buttons:
                    br = rect(b, sw, sh)
                    if br[1] + br[3] > sh:
                        errors.append(f"{tag}: {b['instance_identifier']} {br} off screen")
                cw = len(COPYRIGHT) * GLYPH_MAX_W
                copy_r = (sw - cw - 2, sh - 10, cw, 10)  # TitleScreen.init: PlainTextButton(width - w - 2, height - 10)
                if overlap(text_r, copy_r):
                    errors.append(f"{tag}: version text overlaps the copyright line {copy_r}")
                title_r = (0, 40, sw, FONT_LINE)  # PauseScreen: StringWidget(0, 40, width, 9)
                for i, b in pause.items():
                    br = rect(b, sw, sh)
                    if br[1] < 0 or br[1] + br[3] > sh or br[0] < 0 or br[0] + br[2] > sw:
                        errors.append(f"{tag}: pause {i} {br} off screen")
                    if overlap(br, title_r):
                        errors.append(f"{tag}: pause {i} {br} overlaps the Game Menu title")
                notes.append(f"{tag}: version box {vr}")

    # optional jar cross-check
    jar = args.jar or (DEFAULT_JAR if DEFAULT_JAR.exists() else None)
    if jar:
        blob = b""
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.endswith(".class") and n.startswith("de/keksuccino/fancymenu/"):
                    blob += z.read(n)
        for screen, ids in KNOWN.items():
            if screen == "drippy_loading_overlay":
                continue
            for i in sorted(ids):
                if i.encode() not in blob:
                    errors.append(f"jar {jar.name}: id {i} not found")
        notes.append(f"KNOWN ids cross-checked against {jar.name}")
    else:
        notes.append("FancyMenu jar not found: KNOWN ids not cross-checked (pass --jar)")

    for n in notes:
        print("  " + n)
    if errors:
        for e in errors:
            print("FAIL " + e)
        print(f"check_menus: {len(errors)} error(s)")
        return 1
    print("check_menus: all checks pass")
    return 0


if __name__ == "__main__":
    sys.exit(main())
