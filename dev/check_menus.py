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
  slideshows every slideshow_name used in a layout (menu_background or element) is a folder under
             config/fancymenu/slideshows/ with properties.txt and images/ (SlideshowHandler's rule), the properties
             name equals the folder name, images/ holds at least one .png/.jpg, and every PNG in it is readable and
             has the width x height the properties declare; slideshows no layout uses are listed as notes
  loading    early window: config/fml.toml earlyWindowProvider is drippy_early_window (otherwise NeoForge's own red
             window shows) and config/drippyloadingscreen/options.txt keeps the bar. Late overlay: fa_loading_bar is a
             progress_bar on Drippy's game_loading_progress placeholder (LoadingOverlay.currentProgress, already
             smoothed by vanilla) in percentage mode WITHOUT FancyMenu's second smoothing; fa_loading_memory is a
             text_v2 line with FancyMenu's usedram and maxram placeholders, anchored bottom-right, whose box holds the
             widest text (5-digit values) and stays on screen and clear of the bar and the wordmark at every tested
             size; with the jars, the three placeholder ids are looked up in FancyMenu's and Drippy's classes
  files      every [source:local] path in a layout and every texture path in config/drippyloadingscreen/options.txt
             exists in the pack
  parser     no property line of a layout trips FancyMenu's PropertiesParser: it strips all whitespace from a line,
             opens a section when the result ends with "{" and closes one when it starts with "}", so a property
             whose value ends with "{" (or a value that continues on a line starting with "}") breaks the layout
  backdrops  (G3) every screen in BACKDROP has a layout (identifier = FancyMenu's universal id) and is enabled in
             customizablemenus.txt by its class; its menu_background equals the title's (same shader source and
             settings; the pause screen in WORLD_VISIBLE has none), it has no wordmark or version line, and FancyMenu's global button style in options.txt uses
             the title buttons' textures and label colours
  shader     the title menu_background is background_type = glsl with use_input = false; its inline_shader_source,
             decoded like PropertyContainer.deserializePropertyValue (%%!serialized_property_newline!%% -> line
             break), equals dev/title-shader/constellation.glsl, defines void mainImage(...) (Shadertoy entry point)
             and has no #version / precision line (FancyMenu's wrapper adds #version 150 and strips such lines)

Usage: python dev/check_menus.py [--jar PATH_TO_FANCYMENU_JAR] [--adastra-jar PATH_TO_AD_ASTRA_JAR]
With --jar (or the jar found in the CurseForge instance), every KNOWN id is also searched in the jar's classes.
Exit code 0 = all checks pass.
"""
import argparse
import math
import re
import struct
import sys
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FM = ROOT / "config" / "fancymenu"
LAYOUTS = FM / "customization"
SLIDESHOWS = FM / "slideshows"
DRIPPY_OPTIONS = ROOT / "config" / "drippyloadingscreen" / "options.txt"
TITLE_SHADER = ROOT / "dev" / "title-shader" / "constellation.glsl"
FM_NEWLINE = "%%!serialized_property_newline!%%"  # PropertyContainer.serializePropertyValue
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
def png_size(path):
    """(width, height) from the IHDR chunk, None if the file is not a PNG."""
    head = path.read_bytes()[:24]
    if head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", head[16:24])


def check_slideshows(layouts, errors, notes):
    """Slideshow names used in layouts resolve to complete slideshow folders; slide files match the properties."""
    shows = {}
    for d in sorted(p for p in SLIDESHOWS.iterdir() if p.is_dir()) if SLIDESHOWS.is_dir() else []:
        props, images = d / "properties.txt", d / "images"
        if not props.is_file() or not images.is_dir():
            errors.append(f"slideshows/{d.name}: needs properties.txt and images/ (FancyMenu skips it otherwise)")
            continue
        meta = dict(parse_layout(props)).get("slideshow-meta", {})
        if meta.get("name") != d.name:
            errors.append(f"slideshows/{d.name}/properties.txt: name '{meta.get('name')}' != folder name")
        files = sorted((f for f in images.iterdir() if f.suffix.lower() in (".png", ".jpg", ".jpeg")),
                       key=lambda f: f.name.lower())
        if not files:
            errors.append(f"slideshows/{d.name}/images: no .png/.jpg slides")
        want = (int(meta["width"]), int(meta["height"])) if "width" in meta and "height" in meta else None
        for f in files:
            if f.suffix.lower() != ".png":
                continue
            size = png_size(f)
            if size is None:
                errors.append(f"slideshows/{d.name}/images/{f.name}: not a PNG")
            elif want and size != want:
                errors.append(f"slideshows/{d.name}/images/{f.name}: {size[0]}x{size[1]}, properties say {want[0]}x{want[1]}")
        shows[d.name] = files
    used = set()
    for screen, (f, secs) in layouts.items():
        for n, d in secs:
            name = d.get("slideshow_name")
            if name is None:
                continue
            used.add(name)
            if name not in shows:
                errors.append(f"{f.name}: {n} {d.get('instance_identifier', '?')} uses slideshow '{name}', "
                              f"no such folder in config/fancymenu/slideshows/")
            else:
                notes.append(f"{f.name}: slideshow '{name}' -> {len(shows[name])} slides "
                             f"({', '.join(x.name for x in shows[name][:1])} .. {shows[name][-1].name})"
                             if shows[name] else f"{f.name}: slideshow '{name}'")
    for name in sorted(set(shows) - used):
        notes.append(f"slideshows/{name} is not used by any layout")


def check_files(layouts, errors):
    """Every local file a layout or Drippy's early loading screen points at exists."""
    for screen, (f, secs) in layouts.items():
        for n, d in secs:
            for k, v in d.items():
                if v.startswith("[source:local]"):
                    rel = v[len("[source:local]"):].lstrip("/")
                    if not (ROOT / rel).is_file():
                        errors.append(f"{f.name}: {d.get('instance_identifier', n)} {k} -> {rel} missing")
    if DRIPPY_OPTIONS.is_file():
        for m in re.finditer(r"^S:(\w+_path) = '([^']+)';", DRIPPY_OPTIONS.read_text(encoding="utf-8"), re.M):
            rel = m[2].lstrip("/")
            if rel and not (ROOT / rel).is_file():
                errors.append(f"drippyloadingscreen/options.txt: {m[1]} -> {rel} missing")


def check_parser(layouts, errors):
    """Replays PropertiesParser.deserializeSetFromFancyString's line rules on every layout line."""
    for screen, (f, secs) in layouts.items():
        inside = False
        for no, raw in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            compact = re.sub(r"\s+", "", raw)
            if compact.endswith("{"):
                if inside or "=" in compact:
                    errors.append(f"{f.name}:{no}: line ends with '{{' inside a section or after '=' "
                                  f"(FancyMenu reads it as a new section)")
                inside = True
            elif compact.startswith("}"):
                if compact != "}":
                    errors.append(f"{f.name}:{no}: line starts with '}}' but is not a lone '}}' "
                                  f"(FancyMenu closes the section there)")
                inside = False


def check_title_shader(layouts, errors, notes):
    """The title background is the constellation shader, inlined unchanged, without mouse input."""
    f, secs = layouts.get("title_screen", (None, []))
    bg = next((d for n, d in secs if n == "menu_background"), None)
    if not bg or bg.get("background_type") != "glsl":
        errors.append("title layout: menu_background is not background_type = glsl")
        return
    for key, want in (("use_input", "false"), ("time_scale", "1.0"), ("freeze_time", "false")):
        if bg.get(key) != want:
            errors.append(f"title layout: menu_background {key} = {bg.get(key)}, expected {want}")
    raw = next((line for line in f.read_text(encoding="utf-8").splitlines()
                if re.match(r"\s*inline_shader_source\s*=", line)), None)
    if raw is None:
        errors.append("title layout: menu_background has no inline_shader_source")
        return
    value = raw.split("=", 1)[1]
    value = value[1:] if value.startswith(" ") else value  # PropertiesParser drops one leading space
    src = value.replace(FM_NEWLINE, "\n")
    want = (TITLE_SHADER.read_text(encoding="utf-8").replace("\r\n", "\n").rstrip("\n")
            if TITLE_SHADER.is_file() else None)
    if want is None:
        errors.append(f"{TITLE_SHADER.relative_to(ROOT).as_posix()} missing")
    elif src != want:
        errors.append(f"title layout: inline_shader_source differs from {TITLE_SHADER.relative_to(ROOT).as_posix()} "
                      f"(run python dev/title-shader/build_shader.py --layout)")
    if not re.search(r"\bvoid\s+mainImage\s*\(", src):
        errors.append("title layout: shader has no void mainImage(...)")
    if re.search(r"(?m)^\s*(#\s*version|precision)\b", src):
        errors.append("title layout: shader has a #version or precision line (FancyMenu's wrapper sets #version 150)")
    notes.append(f"title shader: {len(src.splitlines())} lines inlined, use_input = {bg.get('use_input')}, "
                 f"show_compile_errors = {bg.get('show_compile_errors', 'false')}")


LOADING_PLACEHOLDERS = {"usedram": "fancymenu", "maxram": "fancymenu", "game_loading_progress": "drippyloadingscreen"}
DRIPPY_JAR = INSTANCE_MODS / "drippyloadingscreen_neoforge_3.1.5_MC_1.21.1.jar"


def check_loading(layouts, errors, notes, fancymenu_jar):
    fml = ROOT / "config" / "fml.toml"
    text = fml.read_text(encoding="utf-8") if fml.exists() else ""
    if not re.search(r'^earlyWindowProvider\s*=\s*"drippy_early_window"\s*$', text, re.M):
        errors.append("config/fml.toml: earlyWindowProvider is not drippy_early_window (NeoForge's red window shows)")
    opts = DRIPPY_OPTIONS.read_text(encoding="utf-8")
    if not re.search(r"^B:early_loading_hide_bar = 'false';$", opts, re.M):
        errors.append("drippyloadingscreen/options.txt: early_loading_hide_bar must be 'false'")
    f, secs = layouts.get("drippy_loading_overlay", (None, []))
    el = {d.get("instance_identifier"): d for n, d in secs if n == "element"}
    bar, mem, word = el.get("fa_loading_bar"), el.get("fa_loading_memory"), el.get("fa_loading_wordmark")
    if not bar:
        errors.append("loading layout: fa_loading_bar missing")
    else:
        want = {"element_type": "progress_bar", "progress_source": '{"placeholder":"game_loading_progress"}',
                "value_mode": "percentage", "smooth_filling_animation": "false"}
        for k, v in want.items():
            if bar.get(k) != v:
                errors.append(f"loading layout: fa_loading_bar {k} = {bar.get(k)!r}, expected {v!r}")
    if not mem:
        errors.append("loading layout: fa_loading_memory missing")
        return
    src = mem.get("source", "")
    if mem.get("element_type") != "text_v2" or mem.get("source_mode") != "direct":
        errors.append("loading layout: fa_loading_memory is not a direct text_v2 element")
    for ph in ("usedram", "maxram"):
        if '{"placeholder":"%s"}' % ph not in src:
            errors.append(f"loading layout: fa_loading_memory does not show the {ph} placeholder")
    if mem.get("anchor_point") != "bottom-right":
        errors.append("loading layout: fa_loading_memory is not anchored bottom-right")
    shown = re.sub(r'\{"placeholder":"[a-z_]+"\}', "99999", src)
    border = int(mem.get("text_border", "2"))
    need_w = 2 * 1 + 2 * border + GLYPH_MAX_W * len(shown)
    need_h = 2 * 1 + 2 * border + FONT_LINE + 1
    if int(mem["width"]) < need_w or int(mem["height"]) < need_h:
        errors.append(f"loading layout: memory box {mem['width']}x{mem['height']}, '{shown}' needs {need_w}x{need_h}")
    for fw, fh in SCREENS:
        for gs in GUI_SCALES:
            s = gs or auto_scale(fw, fh)
            sw, sh = scaled(fw, fh, s)
            mr = rect(mem, sw, sh)
            if mr[0] < 2 or mr[1] < 0 or mr[0] + mr[2] > sw - 2 or mr[1] + mr[3] > sh - 2:
                errors.append(f"{fw}x{fh} GUI {gs or 'auto'}: memory box {mr} not inside the screen with a 2 px inset")
            for other in (bar, word):
                if other and overlap(mr, rect(other, sw, sh)):
                    errors.append(f"{fw}x{fh} GUI {gs or 'auto'}: memory box {mr} overlaps {other['instance_identifier']}")
    jars = {"fancymenu": fancymenu_jar, "drippyloadingscreen": DRIPPY_JAR if DRIPPY_JAR.exists() else None}
    for ph, owner in LOADING_PLACEHOLDERS.items():
        jar = jars[owner]
        if not jar:
            notes.append(f"placeholder {ph}: {owner} jar not found, not cross-checked")
            continue
        with zipfile.ZipFile(jar) as z:
            hit = any(n.endswith(".class") and "placeholder" in n.lower() and
                      bytes([1]) + struct.pack(">H", len(ph)) + ph.encode() in z.read(n) for n in z.namelist())
        if not hit:
            errors.append(f"jar {jar.name}: placeholder id {ph} not found")
    notes.append(f"loading overlay: bar on game_loading_progress, memory line '{shown}' in {mem['width']}x{mem['height']}")


# G3 (Felix): the title backdrop on every menu screen; universal id -> class (UniversalScreenIdentifierRegistry, javap
# FancyMenu 3.9.14; key_binds_screen is registered there with KeyBindsList, the screen class is KeyBindsScreen).
BACKDROP = {
    "select_world_screen": "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen",
    "create_world_screen": "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen",
    "edit_world_screen": "net.minecraft.client.gui.screens.worldselection.EditWorldScreen",
    "experiments_screen": "net.minecraft.client.gui.screens.worldselection.ExperimentsScreen",
    "edit_game_rules_screen": "net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen",
    "join_multiplayer_screen": "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen",
    "direct_join_server_screen": "net.minecraft.client.gui.screens.DirectJoinServerScreen",
    "edit_server_screen": "net.minecraft.client.gui.screens.EditServerScreen",
    "options_screen": "net.minecraft.client.gui.screens.options.OptionsScreen",
    "video_settings_screen": "net.minecraft.client.gui.screens.options.VideoSettingsScreen",
    "sound_options_screen": "net.minecraft.client.gui.screens.options.SoundOptionsScreen",
    "controls_screen": "net.minecraft.client.gui.screens.options.controls.ControlsScreen",
    "key_binds_screen": "net.minecraft.client.gui.screens.options.controls.KeyBindsScreen",
    "mouse_settings_screen": "net.minecraft.client.gui.screens.options.MouseSettingsScreen",
    "language_select_screen": "net.minecraft.client.gui.screens.options.LanguageSelectScreen",
    "chat_options_screen": "net.minecraft.client.gui.screens.options.ChatOptionsScreen",
    "skin_customization_screen": "net.minecraft.client.gui.screens.options.SkinCustomizationScreen",
    "accessibility_options_screen": "net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen",
    "online_options_screen": "net.minecraft.client.gui.screens.options.OnlineOptionsScreen",
    "pack_selection_screen": "net.minecraft.client.gui.screens.packs.PackSelectionScreen",
    "connect_screen": "net.minecraft.client.gui.screens.ConnectScreen",
    "level_loading_screen": "net.minecraft.client.gui.screens.LevelLoadingScreen",
    "receiving_level_screen": "net.minecraft.client.gui.screens.ReceivingLevelScreen",
    "generic_dirt_message_screen": "net.minecraft.client.gui.screens.GenericMessageScreen",
    "generic_waiting_screen": "net.minecraft.client.gui.screens.GenericWaitingScreen",
    "progress_screen": "net.minecraft.client.gui.screens.ProgressScreen",
    "disconnected_screen": "net.minecraft.client.gui.screens.DisconnectedScreen",
    "pause_screen": "net.minecraft.client.gui.screens.PauseScreen",
}

# Screens over a running world keep the vanilla transparent background (Felix 2026-10-18): only the button styles.
WORLD_VISIBLE = {"pause_screen"}


def check_backdrops(layouts, cm, errors):
    _, tsecs = layouts.get("title_screen", (None, []))
    tbg = next((d for n, d in tsecs if n == "menu_background"), None)
    tbuttons = [d for n, d in tsecs if n == "vanilla_button" and d.get("is_hidden") != "true"]
    if tbg is None or not tbuttons:
        errors.append("backdrops: title layout has no menu_background or no restyled button")
        return
    for ident, cls in BACKDROP.items():
        if not re.search(r"^" + re.escape(cls) + r" \{$", cm, re.M):
            errors.append(f"customizablemenus.txt: {cls} not enabled")
        if ident not in layouts:
            errors.append(f"backdrops: no layout for {ident}")
            continue
        f, secs = layouts[ident]
        bg = next((d for n, d in secs if n == "menu_background"), None)
        if ident in WORLD_VISIBLE:
            if bg is not None:
                errors.append(f"{f.name}: menu_background set; the world stays visible behind this screen")
        elif bg != tbg:
            errors.append(f"{f.name}: menu_background differs from the title's")
        for n, d in secs:
            if d.get("instance_identifier") in ("fa_title_wordmark", "fa_title_version"):
                errors.append(f"{f.name}: {d['instance_identifier']} belongs on the title screen only")
        for d in secs:
            if d[0] == "vanilla_button" and d[1].get("is_hidden") != "true":
                for k in ("backgroundnormal", "backgroundhovered", "label_base_color", "label_hover_color"):
                    if d[1].get(k) != tbuttons[0].get(k):
                        errors.append(f"{f.name}: {d[1].get('instance_identifier')} {k} differs from the title buttons")
    opts = (FM / "options.txt").read_text(encoding="utf-8")
    want = {"global_button_background_normal": tbuttons[0].get("backgroundnormal"),
            "global_button_background_hover": tbuttons[0].get("backgroundhovered"),
            "global_button_label_base_color": tbuttons[0].get("label_base_color"),
            "global_button_label_hover_color": tbuttons[0].get("label_hover_color")}
    for k, v in want.items():
        m = re.search(rf"^S:{k} = '([^']*)';$", opts, re.M)
        if not m or m[1] != v:
            errors.append(f"fancymenu/options.txt: {k} = {m[1] if m else None!r}, expected {v!r} (title buttons)")


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

    check_slideshows(layouts, errors, notes)
    check_files(layouts, errors)
    check_parser(layouts, errors)
    check_title_shader(layouts, errors, notes)
    check_backdrops(layouts, cm, errors)

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
    check_loading(layouts, errors, notes, jar)
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
