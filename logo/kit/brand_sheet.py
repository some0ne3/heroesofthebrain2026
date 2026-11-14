"""Brand overview sheet (dark + light). Run after build_kit.py:  py -3 brand_sheet.py"""
import os
from build_kit import SCHEMES, INK_DARK, PAPER

HERE = os.path.dirname(os.path.abspath(__file__))
svg = lambda name: open(os.path.join(HERE, "svg", name + ".svg"), encoding="utf-8").read()
icon = lambda name: open(os.path.join(HERE, "icons", name + ".svg"), encoding="utf-8").read()


def panel(theme, bg, fg, sub):
    o, i, p, wm = SCHEMES[theme]
    sw = "".join(f'<div class="sw"><i style="background:{c}"></i><b>{n}</b><span>{c}</span></div>'
                 for n, c in (("Ember", o), ("Flame", i), ("Pupil", p), ("Ink" if theme == "light" else "Cream", wm),
                              ("Base", bg)))
    return f"""
<section style="background:{bg};color:{fg}">
  <h2>{theme.title()} theme <small style="color:{sub}">· logo na tło {bg}</small></h2>
  <div class="hero">{svg(f"freehand-horizontal-{theme}")}</div>
  <div class="grid">
    <div class="box stacked">{svg(f"freehand-stacked-{theme}")}</div>
    <div class="box sym">{svg(f"freehand-symbol-{theme}")}</div>
    <div class="box word">{svg(f"freehand-wordmark-{theme}")}</div>
  </div>
  <div class="row">
    <div class="ic">{icon("app-icon-dark")}</div><div class="ic">{icon("app-icon-light")}</div>
    <div class="ic round">{icon("app-icon-round")}</div>
    <div class="ic f32">{icon("favicon")}</div><div class="ic f16">{icon("favicon")}</div>
    <div class="mono">{svg("freehand-horizontal-white" if theme == "dark" else "freehand-horizontal-black")}</div>
  </div>
  <div class="pal">{sw}</div>
</section>"""


html = f"""<!doctype html><meta charset="utf-8"><title>Freehand brand sheet</title>
<style>
body{{margin:0;width:1600px;font:15px 'Segoe UI',sans-serif;background:#000}}
section{{padding:44px 56px}}
h2{{font:600 22px Bahnschrift;margin:0 0 26px}} small{{font-weight:400}}
svg{{display:block;height:100%;width:auto}}
.hero{{height:110px;margin-bottom:40px}}
.grid{{display:flex;gap:80px;align-items:center;margin-bottom:40px}}
.stacked{{height:230px}} .sym{{height:100px}} .word{{height:60px}}
.row{{display:flex;gap:28px;align-items:flex-end;margin-bottom:30px}}
.ic{{width:112px;height:112px}} .f32{{width:32px;height:32px}} .f16{{width:16px;height:16px}}
.mono{{width:360px;height:52px;margin-left:30px}}
.pal{{display:flex;gap:14px}} .sw{{display:flex;flex-direction:column;gap:4px;font-size:12px}}
.sw i{{width:120px;height:56px;border-radius:12px;outline:1px solid #8884}} .sw span{{opacity:.7;font-family:Consolas}}
</style>
{panel("dark", INK_DARK, "#F7EFE6", "#9A8E84")}
{panel("light", PAPER, "#1E1814", "#7A6E64")}"""
open(os.path.join(HERE, "brand-sheet.html"), "w", encoding="utf-8").write(html)
print("ok")
