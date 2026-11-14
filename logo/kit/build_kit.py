"""Build the Freehand logo kit (concept B "Wave Eye", palette 3 "Warm ramp").

Run from anywhere:  py -3 build_kit.py            (SVG + XML + HTML only)
                    py -3 build_kit.py --png      (also renders PNG/ICO via the logo-design skill)
"""
import math, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
RENDER = os.path.expanduser("~/.claude/skills/logo-design/scripts/render_png.py")
F = lambda v: f"{v:.2f}".rstrip("0").rstrip(".")

# ---------------- colour ----------------
INK_DARK, PAPER = "#16120F", "#FAF6F1"
SCHEMES = {  # name -> (outer bars, inner bars, pupil, wordmark); name = background it is made for
    "dark":  ("#E5483A", "#FF7A45", "#FFC247", "#F7EFE6"),
    "light": ("#C93328", "#E35A26", "#EA9A0E", "#1E1814"),
    "black": ("#000000",) * 4,
    "white": ("#FFFFFF",) * 4,
}

# ---------------- symbol geometry (256 grid, same as concept B) ----------------
BW, GAP, PD, CY = 26, 14, 68, 128
BARS = [("bar1", 36), ("bar2", 104), ("pupil", None), ("bar4", 104), ("bar5", 36)]


def symbol_shapes(small=False):
    """[(id, role, kind, params)] ; small = 3-element cut for favicons."""
    items = BARS[1:4] if small else BARS
    total = sum(PD if h is None else BW for _, h in items) + GAP * (len(items) - 1)
    x, out = (256 - total) / 2, []
    for id_, h in items:
        if h is None:
            out.append((id_, "pupil", "circle", (x + PD / 2, CY, PD / 2))); x += PD + GAP
        else:
            role = "outer" if h < 60 else "inner"
            out.append((id_, role, "bar", (x, CY - h / 2, BW, h))); x += BW + GAP
    return out


def bbox(shapes):
    xs, ys = [], []
    for *_, kind, p in shapes:
        if kind == "circle":
            cx, cy, r = p; xs += [cx - r, cx + r]; ys += [cy - r, cy + r]
        else:
            x, y, w, h = p; xs += [x, x + w]; ys += [y, y + h]
    return min(xs), min(ys), max(xs), max(ys)


def shape_path(kind, p):
    """Path data (used for VectorDrawable and for a single-path variant)."""
    if kind == "circle":
        cx, cy, r = p
        return f"M{F(cx - r)},{F(cy)} A{F(r)},{F(r)} 0 1 1 {F(cx + r)},{F(cy)} A{F(r)},{F(r)} 0 1 1 {F(cx - r)},{F(cy)} Z"
    x, y, w, h = p; r = w / 2
    return (f"M{F(x)},{F(y + r)} A{F(r)},{F(r)} 0 0 1 {F(x + w)},{F(y + r)} V{F(y + h - r)} "
            f"A{F(r)},{F(r)} 0 0 1 {F(x)},{F(y + h - r)} Z")


def symbol_svg_group(scheme, small=False, indent="  "):
    o, i, p, _ = SCHEMES[scheme]
    col = {"outer": o, "inner": i, "pupil": p}
    lines = [f'{indent}<g id="symbol">']
    for id_, role, kind, prm in symbol_shapes(small):
        if kind == "circle":
            cx, cy, r = prm
            lines.append(f'{indent}  <circle id="{id_}" cx="{F(cx)}" cy="{F(cy)}" r="{F(r)}" fill="{col[role]}"/>')
        else:
            x, y, w, h = prm
            lines.append(f'{indent}  <rect id="{id_}" x="{F(x)}" y="{F(y)}" width="{F(w)}" height="{F(h)}" rx="{F(w / 2)}" fill="{col[role]}"/>')
    return "\n".join(lines + [f"{indent}</g>"])


# ---------------- wordmark: custom monoline "freehand" ----------------
W = 20                     # stroke, on a 100-unit x-height
R = 50 - W / 2             # bowl radius (centreline)
ASC, XH, BASE, MID = -150 + W / 2, -100 + W / 2, -W / 2, -50
RF = 26                    # f hook radius
c45 = R * math.cos(math.radians(40))  # e terminal angle


def glyph(ch):
    """centreline paths in a box whose left stem/bowl edge is x=0; returns (paths, left, right)."""
    bowl = f"M{F(2 * R)} {MID} A{F(R)} {F(R)} 0 1 0 0 {MID} A{F(R)} {F(R)} 0 1 0 {F(2 * R)} {MID}"
    arch = f"M0 {MID} A{F(R)} {F(R)} 0 0 1 {F(2 * R)} {MID} V{F(BASE)}"
    if ch == "f":
        return [f"M0 {F(BASE)} V{F(ASC + RF)} A{RF} {RF} 0 0 1 {RF} {F(ASC)} H{RF + 8}",
                f"M-18 {F(XH)} H30"], -18, RF + 8
    if ch == "r":
        return [f"M0 {F(BASE)} V{F(XH)}", f"M0 {MID} A{F(R)} {F(R)} 0 0 1 {F(R)} {F(XH)} H{F(R + 8)}"], 0, R + 8
    if ch == "e":
        return [f"M0 {MID} H{F(2 * R)} A{F(R)} {F(R)} 0 1 0 {F(R + c45)} {F(MID + R * math.sin(math.radians(40)))}"], 0, 2 * R
    if ch == "h":
        return [f"M0 {F(BASE)} V{F(ASC)}", arch], 0, 2 * R
    if ch == "n":
        return [f"M0 {F(BASE)} V{F(XH)}", arch], 0, 2 * R
    if ch == "a":
        return [bowl, f"M{F(2 * R)} {F(XH)} V{F(BASE)}"], 0, 2 * R
    if ch == "d":
        return [bowl, f"M{F(2 * R)} {F(ASC)} V{F(BASE)}"], 0, 2 * R
    raise ValueError(ch)


WORD = "freehand"
KERN = {"fr": -8, "re": -4, "ee": -6, "eh": -2, "ha": -4, "an": 0, "nd": -4}
GAP_W = 19  # stroke-edge to stroke-edge


def wordmark_layout():
    x, out, prev, seen = 0, [], None, {}
    for ch in WORD:
        paths, l, r = glyph(ch)
        if prev:
            x += GAP_W + W + KERN.get(prev[0] + ch, 0) - l
        else:
            x = -l
        seen[ch] = seen.get(ch, 0) + 1
        gid = ch + (str(seen[ch]) if WORD.count(ch) > 1 else "")
        out.append((gid, x, paths))
        x += r
        prev = (ch,)
    width = x + W / 2  # right outer edge (left outer edge = -W/2 + ...)
    return out, width


def wordmark_svg_group(colour, indent="  "):
    letters, _ = wordmark_layout()
    lines = [f'{indent}<g id="wordmark" fill="none" stroke="{colour}" stroke-width="{W}" '
             f'stroke-linecap="round" stroke-linejoin="round">']
    for gid, x, paths in letters:
        d = " ".join(paths)
        lines.append(f'{indent}  <path id="{gid}" transform="translate({F(x)} 0)" d="{d}"/>')
    return "\n".join(lines + [f"{indent}</g>"])


WM_R = wordmark_layout()[1]          # outer right edge
WM_L = -W / 2                        # outer left edge (f crossbar)
WM_TOP, WM_BOT = -150, 0


def svg_doc(body, vb, title):
    x, y, ww, hh = vb  # viewBox only: scales freely in CSS / Remotion
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{F(x)} {F(y)} {F(ww)} {F(hh)}">\n'
            f'  <title>{title}</title>\n{body}\n</svg>\n')


# ---------------- assemblies ----------------
SX0, SY0, SX1, SY1 = bbox(symbol_shapes())          # 14 76 242 180
SW, SH = SX1 - SX0, SY1 - SY0


def make_symbol(s):
    return svg_doc(symbol_svg_group(s), (SX0, SY0, SW, SH), "Freehand")


def make_wordmark(s):
    col = SCHEMES[s][3]
    return svg_doc(wordmark_svg_group(col), (WM_L, WM_TOP, WM_R - WM_L, WM_BOT - WM_TOP), "Freehand")


def make_horizontal(s):
    k = 0.6                                     # wordmark x-height = 60
    gap = 46
    tx = SX1 + gap - WM_L * k
    ty = CY + 50 * k                            # x-height centre on the symbol's centre line
    body = (symbol_svg_group(s) + "\n" + f'  <g transform="translate({F(tx)} {F(ty)}) scale({k})">\n'
            + wordmark_svg_group(SCHEMES[s][3], "    ") + "\n  </g>")
    right = tx + WM_R * k
    top = min(SY0, ty + WM_TOP * k)
    bot = max(SY1, ty)
    return svg_doc(body, (SX0, top, right - SX0, bot - top), "Freehand")


def make_stacked(s):
    k, ss, gap = 0.6, 1.4, 48
    wm_w = (WM_R - WM_L) * k
    sym_w, sym_h = SW * ss, SH * ss
    width = max(wm_w, sym_w)
    sx = (width - sym_w) / 2 - SX0 * ss
    body = f'  <g transform="translate({F(sx)} {F(-SY0 * ss)}) scale({ss})">\n' + symbol_svg_group(s, indent="    ") + "\n  </g>"
    ty = sym_h + gap - WM_TOP * k
    tx = (width - wm_w) / 2 - WM_L * k
    body += (f'\n  <g transform="translate({F(tx)} {F(ty)}) scale({k})">\n'
             + wordmark_svg_group(SCHEMES[s][3], "    ") + "\n  </g>")
    return svg_doc(body, (0, 0, width, ty), "Freehand")


# ---------------- icons ----------------
def tile_svg(bg, scheme, size=1024, radius=0.2235, mark_w=0.62, small=False, circle=False, full_bleed=False):
    shapes = symbol_shapes(small)
    x0, y0, x1, y1 = bbox(shapes)
    s = size * mark_w / (x1 - x0)
    tx, ty = size / 2 - (x0 + x1) / 2 * s, size / 2 - (y0 + y1) / 2 * s
    if full_bleed:
        bgel = f'<rect width="{size}" height="{size}" fill="{bg}"/>'
    elif circle:
        bgel = f'<circle cx="{size / 2}" cy="{size / 2}" r="{size / 2}" fill="{bg}"/>'
    else:
        bgel = f'<rect width="{size}" height="{size}" rx="{F(size * radius)}" fill="{bg}"/>'
    body = f'  {bgel}\n  <g transform="translate({F(tx)} {F(ty)}) scale({F(s)})">\n' + symbol_svg_group(scheme, small, "    ") + "\n  </g>"
    return svg_doc(body, (0, 0, size, size), "Freehand")


def vector_drawable(scheme, mono=False):
    """Adaptive-icon foreground: 108dp viewport, mark inside the 66dp safe circle."""
    o, i, p, _ = SCHEMES[scheme]
    col = {"outer": o, "inner": i, "pupil": p}
    shapes = symbol_shapes()
    def reach(k, prm):  # farthest distance of a shape from the canvas centre
        if k == "circle":
            cx, cy, r = prm; return math.hypot(cx - 128, cy - 128) + r
        x, y, bw, h = prm; r = bw / 2
        return max(math.hypot(x + r - 128, yy - 128) for yy in (y + r, y + h - r)) + r
    far = max(reach(k, prm) for *_, k, prm in shapes)
    s = 31 / far  # keep everything within r=31dp (safe zone r=33dp)
    t = 54 - 128 * s
    paths = "\n".join(
        f'        <path android:fillColor="{"#FFFFFFFF" if mono else col[role]}" android:pathData="{shape_path(k, prm)}"/>'
        for _, role, k, prm in shapes)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            f'    <group android:scaleX="{s:.5f}" android:scaleY="{s:.5f}"\n'
            f'        android:translateX="{t:.4f}" android:translateY="{t:.4f}">\n'
            f'{paths}\n    </group>\n</vector>\n')


ADAPTIVE = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background"/>\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
            '</adaptive-icon>\n')


# ---------------- contrast ----------------
def lum(hx):
    c = [int(hx[k:k + 2], 16) / 255 for k in (1, 3, 5)]
    c = [v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4 for v in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def contrast(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


# ---------------- write ----------------
def w(rel, text):
    p = os.path.join(HERE, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w", encoding="utf-8", newline="\n").write(text)
    return p


def render(svg, png, width, height=None, bg=None):
    os.makedirs(os.path.dirname(png), exist_ok=True)  # renderer does not create folders
    cmd = [sys.executable, RENDER, svg, "-o", png, "--width", str(width), "--height", str(height or width)]
    if bg:
        cmd += ["--bg", bg]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)


def main(do_png):
    made = []
    for s in SCHEMES:
        made.append(w(f"svg/freehand-symbol-{s}.svg", make_symbol(s)))
        made.append(w(f"svg/freehand-wordmark-{s}.svg", make_wordmark(s)))
        made.append(w(f"svg/freehand-horizontal-{s}.svg", make_horizontal(s)))
        made.append(w(f"svg/freehand-stacked-{s}.svg", make_stacked(s)))
    # app + web icons
    w("icons/app-icon-dark.svg", tile_svg(INK_DARK, "dark"))
    w("icons/app-icon-light.svg", tile_svg(PAPER, "light"))
    w("icons/app-icon-round.svg", tile_svg(INK_DARK, "dark", circle=True))
    w("icons/play-store-512.svg", tile_svg(INK_DARK, "dark", size=512, full_bleed=True))
    w("icons/favicon.svg", tile_svg(INK_DARK, "dark", size=256, radius=0.22, mark_w=0.74, small=True))
    w("icons/maskable-512.svg", tile_svg(INK_DARK, "dark", size=512, full_bleed=True, mark_w=0.5))
    res = "icons/android/res/"
    w(res + "drawable/ic_launcher_foreground.xml", vector_drawable("dark"))
    w(res + "drawable/ic_launcher_monochrome.xml", vector_drawable("dark", mono=True))
    w(res + "mipmap-anydpi-v26/ic_launcher.xml", ADAPTIVE)
    w(res + "mipmap-anydpi-v26/ic_launcher_round.xml", ADAPTIVE)
    w(res + "values/ic_launcher_background.xml",
      f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">{INK_DARK}</color>\n</resources>\n')

    print("contrast vs background (logo marks: aim >= 3:1):")
    for s, bg in (("dark", INK_DARK), ("light", PAPER)):
        print(f"  {s:5} on {bg}: " + ", ".join(f"{c} {contrast(c, bg):.1f}" for c in SCHEMES[s]))

    if not do_png:
        return
    for svg in made:
        rel = os.path.relpath(svg, os.path.join(HERE, "svg"))
        png = os.path.join(HERE, "png", rel[:-4] + ".png")
        os.makedirs(os.path.dirname(png), exist_ok=True)
        vb = [float(v) for v in open(svg).read().split('viewBox="')[1].split('"')[0].split()]
        width = 2048
        render(svg, png, width, round(width * vb[3] / vb[2]))
    ic = os.path.join(HERE, "icons")
    for name in ("app-icon-dark", "app-icon-light", "app-icon-round"):
        render(os.path.join(ic, name + ".svg"), os.path.join(ic, name + "-1024.png"), 1024)
    render(os.path.join(ic, "play-store-512.svg"), os.path.join(ic, "play-store-512.png"), 512)
    for d, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        render(os.path.join(ic, "app-icon-dark.svg"), os.path.join(ic, f"android/res/mipmap-{d}/ic_launcher.png"), px)
        render(os.path.join(ic, "app-icon-round.svg"), os.path.join(ic, f"android/res/mipmap-{d}/ic_launcher_round.png"), px)
    web = os.path.join(HERE, "icons", "web")
    os.makedirs(web, exist_ok=True)
    fav = os.path.join(ic, "favicon.svg")
    for px in (16, 32, 48):
        render(fav, os.path.join(web, f"favicon-{px}.png"), px)
    render(os.path.join(ic, "play-store-512.svg"), os.path.join(web, "apple-touch-icon.png"), 180)
    render(os.path.join(ic, "app-icon-dark.svg"), os.path.join(web, "icon-192.png"), 192)
    render(os.path.join(ic, "app-icon-dark.svg"), os.path.join(web, "icon-512.png"), 512)
    render(os.path.join(ic, "maskable-512.svg"), os.path.join(web, "maskable-512.png"), 512)
    subprocess.run([sys.executable, RENDER, fav, "--ico", os.path.join(web, "favicon.ico"), "--ico-sizes", "16", "32", "48"],
                   check=True, stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    main("--png" in sys.argv)
    print("ok")
