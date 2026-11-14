"""Warm colour study for concept B (Wave Eye). Run: py -3 color_study.py -> color-study.html"""
import os, re
from gen import concept_b

OUT = os.path.dirname(os.path.abspath(__file__))
DARK, LIGHT, WARM_WHITE = "#16120F", "#FAF6F1", "#F7EFE6"

# name -> (outer bars, inner bars, pupil, note)
PALETTES = {
    "1 Amber mono": ("#FFB020", "#FFB020", "#FFB020", "jeden kolor, najprostszy"),
    "2 Coral + amber pupil": ("#FF6B4A", "#FF6B4A", "#FFC247", "źrenica jako akcent sygnału"),
    "3 Warm ramp": ("#E5483A", "#FF7A45", "#FFC247", "od krawędzi do środka robi się jaśniej"),
    "4 Ember (coral mono)": ("#FF5A3C", "#FF5A3C", "#FF5A3C", "mocny koral, jeden kolor"),
}


def mark(outer, inner, pupil, size):
    s = concept_b().split("\n", 1)[1].rsplit("</svg>", 1)[0].replace(' fill="#000"', "")
    for el, col in (("bar1", outer), ("bar5", outer), ("bar2", inner), ("bar4", inner), ("pupil", pupil)):
        s = re.sub(rf'(id="{el}")', rf'\1 fill="{col}"', s)
    return f'<svg viewBox="0 0 256 256" width="{size}" height="{size}">{s}</svg>'


def tile(bg, inner_svg, size=96, radius=0.22):
    return (f'<div style="width:{size}px;height:{size}px;border-radius:{size * radius}px;background:{bg};'
            f'display:flex;align-items:center;justify-content:center">{inner_svg}</div>')


rows = []
for name, (o, i, p, note) in PALETTES.items():
    lock = (f'<div style="display:flex;align-items:center;gap:14px">{mark(o, i, p, 72)}'
            f'<span style="font:600 46px Bahnschrift;color:{WARM_WHITE};letter-spacing:-.5px">freehand</span></div>')
    rows.append(f"""
<div class="row">
  <div class="lbl"><b>{name}</b><br><span>{note}</span><br><code>{o} · {i} · {p}</code></div>
  <div class="cell" style="background:{DARK}">{mark(o, i, p, 140)}</div>
  <div class="cell" style="background:{LIGHT}">{mark(o, i, p, 140)}</div>
  <div class="cell" style="background:{DARK}">{lock}</div>
  <div class="cell" style="background:{LIGHT};gap:18px">{tile(DARK, mark(o, i, p, 66))}{tile(o, mark(DARK, DARK, DARK, 66))}</div>
  <div class="cell" style="background:{DARK};gap:14px;align-items:end">{mark(o, i, p, 32)}{mark(o, i, p, 16)}</div>
</div>""")

html = f"""<!doctype html><meta charset="utf-8"><title>Freehand colour study</title>
<style>
body{{margin:0;padding:28px;background:#EDE7E0;font:14px 'Segoe UI',sans-serif;color:#222;width:1400px}}
h1{{font:600 26px Bahnschrift;margin:0 0 18px}}
.row{{display:grid;grid-template-columns:190px 180px 180px 360px 240px 120px;gap:10px;margin-bottom:10px}}
.cell{{height:170px;border-radius:12px;display:flex;align-items:center;justify-content:center}}
.lbl{{align-self:center;line-height:1.5}} .lbl span{{color:#666}} code{{font-size:11px;color:#555}}
.hdr{{font-size:12px;color:#666;text-transform:uppercase;letter-spacing:.06em}}
</style>
<h1>Freehand — B "Wave Eye" · warm colour study</h1>
<div class="row" style="margin-bottom:4px"><div></div><div class="hdr">ciemne tło</div><div class="hdr">jasne tło</div>
<div class="hdr">lockup (dark mode)</div><div class="hdr">ikona aplikacji</div><div class="hdr">32 / 16 px</div></div>
{''.join(rows)}"""
open(os.path.join(OUT, "color-study.html"), "w", encoding="utf-8").write(html)
print("ok")
