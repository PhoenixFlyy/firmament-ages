"""Gallery images for the CurseForge modpack project, 1920x1080 each with a caption bar: dev/cf/gallery/*.png

The dedicated server cannot take screenshots, so the gallery is generated: three consecrated shrine stages from
dev/shrine-viewer/render_shrine.py (rings 0, 3 and 8), the build plan of the full shrine, and the ten signature items
from the pack's item textures. Sky background, fonts and halo come from dev/cf/make_logo.py.

Usage: python dev/cf/make_gallery.py        (needs dev/shrine-viewer/out/plan_after_age_8_consecrated.png from a
       previous render_shrine.py run; the shrine cutouts are rendered here into dev/shrine-viewer/out/)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "dev" / "shrine-viewer"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import render_shrine as rs  # noqa: E402
from make_logo import FONT_SUB, FONT_TITLE, GOLD, place, sky  # noqa: E402

OUT = ROOT / "dev" / "cf" / "gallery"
VIEWER_OUT = ROOT / "dev" / "shrine-viewer" / "out"
ITEMS = ROOT / "kubejs" / "assets" / "firmages" / "textures" / "item"
W, H, BAR = 1920, 1080, 120
SIGNATURE = [("hearthstone", "Hearthstone", "Stone Age"), ("sky_disc", "Sky Disc", "Bronze Age"),
             ("steel_heart", "Steel Heart", "Iron Age"), ("arcane_keystone", "Arcane Keystone", "Arcane Age"),
             ("pressure_core", "Pressure Core", "Industrial Age"), ("humming_core", "Humming Core", "Electric Age"),
             ("data_matrix", "Data Matrix", "Information Age"), ("star_chart", "Star Chart", "Space Age"),
             ("quantum_core", "Quantum Core", "Quantum Age"),
             ("ultimate_singularity", "Ultimate Singularity", "Singularity Age")]


def caption(canvas: Image.Image, title: str, sub: str | None = None) -> None:
    bar = Image.new("RGBA", (W, BAR), (8, 6, 20, 215))
    canvas.alpha_composite(bar, (0, H - BAR))
    d = ImageDraw.Draw(canvas)
    d.line((0, H - BAR, W, H - BAR), fill=(244, 214, 140, 160), width=2)
    f1 = ImageFont.truetype(FONT_TITLE, 40 if sub else 46)
    if sub:
        d.text((W / 2, H - BAR + 38), title, font=f1, fill=GOLD, anchor="mm")
        d.text((W / 2, H - BAR + 86), sub, font=ImageFont.truetype(FONT_SUB, 30), fill=(222, 210, 240), anchor="mm")
    else:
        d.text((W / 2, H - BAR / 2), title, font=f1, fill=GOLD, anchor="mm")


def load_rings():
    data_root = rs.DEFAULT_DATA_ROOT
    lang = rs.load_lang(data_root)
    notes: list[str] = []
    materials: dict[str, rs.Material] = {}
    rings = [r for n in rs.discover_ring_numbers(data_root) if (r := rs.parse_ring(n, data_root, lang, materials, notes))]
    rs.assign_plan_codes(materials)
    rs.assign_roles(rings, rs.load_consecration(data_root, notes), notes)
    cumul = {r.n: rs.cumulative_blocks(rings, r.n, notes) for r in rings}
    look = rs.Look(consecrated=True, roles={r.n: r.roles for r in rings}, accents={r.n: r.beam_color for r in rings},
                   age_names={r.n: r.age_name for r in rings})
    return rings, materials, cumul, look


def shrine_image(ring, blocks, materials, look, seed: int, name: str) -> Path:
    cut = VIEWER_OUT / f"shrine_cutout_age_{ring.n}.png"
    rs.render_cutout(blocks, materials, ring, cut, look)
    im = Image.open(cut).convert("RGBA")
    im = im.crop(im.getbbox())
    c = sky((W, H), seed=seed, stars=300)
    place(c, im, (160, 40, W - 160, H - BAR - 20))
    blessing = f" Blessing: {ring.blessing_name}." if ring.blessing_name else ""
    rings_txt = "Ring 0" if ring.n == 0 else f"Rings 0 to {ring.n}"
    caption(c, f"The Shrine after the {ring.age_name}: {ring.display_name}, consecrated",
            f"{rings_txt}, {len(blocks)} blocks. Offering accepted, Age {ring.n + 1} granted.{blessing}")
    out = OUT / name
    c.convert("RGB").save(out)
    return out


def plan_image() -> Path:
    src = VIEWER_OUT / "plan_after_age_8_consecrated.png"
    if not src.exists():
        raise SystemExit(f"{src} missing: run python dev/shrine-viewer/render_shrine.py first")
    plan = Image.open(src).convert("RGB")
    box_w, box_h = W - 60, H - BAR - 40
    s = min(box_w / plan.width, box_h / plan.height)
    plan = plan.resize((round(plan.width * s), round(plan.height * s)), Image.LANCZOS)
    c = Image.new("RGBA", (W, H), (246, 245, 250, 255))
    c.paste(plan, ((W - plan.width) // 2, 20 + (box_h - plan.height) // 2))
    caption(c, "Build plan of the finished shrine: 21 x 21 blocks, 5 layers, 547 blocks",
            "One ring per Age. In game the Shrine Heart shows a ghost preview of the next ring, the quest book names its materials.")
    out = OUT / "04-shrine-build-plan.png"
    c.convert("RGB").save(out)
    return out


def first_frame(path: Path) -> Image.Image:
    im = Image.open(path).convert("RGBA")
    return im.crop((0, 0, im.width, im.width)) if im.height > im.width else im


def items_image() -> Path:
    c = sky((W, H), seed=23, stars=300)
    d = ImageDraw.Draw(c)
    cols, size = 5, 176
    gap_x, gap_y = 330, 400
    x0 = (W - gap_x * (cols - 1)) // 2
    y0 = 150
    f_name = ImageFont.truetype(FONT_TITLE, 30)
    f_age = ImageFont.truetype(FONT_SUB, 26)
    for i, (file, name, age) in enumerate(SIGNATURE):
        tex = first_frame(ITEMS / f"{file}.png").resize((size, size), Image.NEAREST)
        cx, cy = x0 + gap_x * (i % cols), y0 + gap_y * (i // cols)
        halo = Image.new("RGBA", (size + 80, size + 80), (0, 0, 0, 0))
        ImageDraw.Draw(halo).ellipse((0, 0, size + 80, size + 80), fill=(255, 220, 255, 70))
        from PIL import ImageFilter
        c.alpha_composite(halo.filter(ImageFilter.GaussianBlur(24)), (cx - size // 2 - 40, cy - size // 2 - 40))
        c.alpha_composite(tex, (cx - size // 2, cy - size // 2))
        d.text((cx, cy + size // 2 + 34), name, font=f_name, fill=GOLD, anchor="mm")
        d.text((cx, cy + size // 2 + 70), age, font=f_age, fill=(200, 188, 224), anchor="mm")
    caption(c, "Ten signature items, one per Age",
            "Each one is offered at the shrine, not crafted into the next tier. The relics stay on their plinths until the finale.")
    out = OUT / "05-signature-items.png"
    c.convert("RGB").save(out)
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rings, materials, cumul, look = load_rings()
    by_n = {r.n: r for r in rings}
    written = [
        shrine_image(by_n[0], cumul[0], materials, look, 3, "01-shrine-stone-age.png"),
        shrine_image(by_n[3], cumul[3], materials, look, 5, "02-shrine-arcane-age.png"),
        shrine_image(by_n[8], cumul[8], materials, look, 9, "03-shrine-quantum-age.png"),
        plan_image(),
        items_image(),
    ]
    for p in written:
        im = Image.open(p)
        print(f"{p.relative_to(ROOT)}: {im.width}x{im.height}")


if __name__ == "__main__":
    main()
