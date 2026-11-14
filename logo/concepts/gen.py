"""Generate Freehand logo concepts (greyscale SVG). Run: py -3 gen.py"""
import math, os

OUT = os.path.dirname(os.path.abspath(__file__))
F = lambda v: f"{v:.2f}".rstrip("0").rstrip(".")


def pt(p):
    return f"{F(p[0])} {F(p[1])}"


def rounded_polygon(pts, r):
    """Closed path through convex polygon pts with corner radius r."""
    n, segs = len(pts), []
    for i in range(n):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % n]
        a = (p0[0] - p1[0], p0[1] - p1[1]); b = (p2[0] - p1[0], p2[1] - p1[1])
        la, lb = math.hypot(*a), math.hypot(*b)
        ang = math.acos((a[0] * b[0] + a[1] * b[1]) / (la * lb))
        d = r / math.tan(ang / 2)
        s = (p1[0] + a[0] / la * d, p1[1] + a[1] / la * d)
        e = (p1[0] + b[0] / lb * d, p1[1] + b[1] / lb * d)
        segs.append((s, e))
    path = f"M{pt(segs[0][1])}"
    for i in range(1, n + 1):
        s, e = segs[i % n]
        path += f" L{pt(s)} A{F(r)} {F(r)} 0 0 1 {pt(e)}"
    return path + " Z"


def arc_band(cx, cy, r_in, r_out, phi):
    """Thick arc above centre (cx,cy), spanning +-phi deg from vertical, round caps."""
    def P(r, a):
        a = math.radians(a); return (cx + r * math.sin(a), cy - r * math.cos(a))
    w = (r_out - r_in) / 2
    return (f"M{pt(P(r_out, -phi))} A{F(r_out)} {F(r_out)} 0 0 1 {pt(P(r_out, phi))}"
            f" A{F(w)} {F(w)} 0 0 1 {pt(P(r_in, phi))}"
            f" A{F(r_in)} {F(r_in)} 0 0 0 {pt(P(r_in, -phi))}"
            f" A{F(w)} {F(w)} 0 0 1 {pt(P(r_out, -phi))} Z")


def svg(body, w=256, h=256):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">\n'
            f'{body}\n</svg>\n')


# ---------- A: Lid & Play — headband = eyelid, pupil = play button ----------
def concept_a():
    x0, x1, yc, sag = 40, 216, 147, 50
    c = (x1 - x0) / 2
    r = (c * c + sag * sag) / (2 * sag)
    lens = (f"M{x0} {yc} A{F(r)} {F(r)} 0 0 1 {x1} {yc} "
            f"A{F(r)} {F(r)} 0 0 1 {x0} {yc} Z")
    th = 50 * math.tan(math.radians(30))  # exact 30 deg play edges
    tri = rounded_polygon([(113, yc - th), (163, yc), (113, yc + th)], 6)
    cy = yc + r - sag  # centre of the upper lens arc
    lid = arc_band(128, cy, r + 15, r + 39, 37)
    return svg(f'<g id="symbol" fill="#000">\n'
               f'  <path id="lid" d="{lid}"/>\n'
               f'  <path id="eye" fill-rule="evenodd" d="{lens} {tri}"/>\n</g>')


# ---------- B: Wave eye — a waveform whose silhouette is an eye ----------
def concept_b():
    bw, gap, d = 26, 14, 68  # bar width, gap, pupil diameter
    hs = [36, 104, None, 104, 36]
    total = bw * 4 + d + gap * 4
    x, parts, cyy = (256 - total) / 2, [], 128
    for i, h in enumerate(hs):
        if h is None:
            parts.append(f'  <circle id="pupil" cx="{F(x + d / 2)}" cy="{cyy}" r="{F(d / 2)}"/>')
            x += d + gap
        else:
            parts.append(f'  <rect id="bar{i + 1}" x="{F(x)}" y="{F(cyy - h / 2)}" width="{bw}" height="{h}" rx="{bw / 2}"/>')
            x += bw + gap
    return svg('<g id="symbol" fill="#000">\n' + "\n".join(parts) + "\n</g>")


# ---------- C: Blink cut — a play button cut by a closed eyelid curve ----------
def circle_line(c, R, p, q):
    """Intersection of circle with segment p->q (t in [0,1]); returns points."""
    dx, dy = q[0] - p[0], q[1] - p[1]
    fx, fy = p[0] - c[0], p[1] - c[1]
    A = dx * dx + dy * dy; B = 2 * (fx * dx + fy * dy); C = fx * fx + fy * fy - R * R
    disc = B * B - 4 * A * C
    out = []
    for s in (-1, 1):
        t = (-B + s * math.sqrt(disc)) / (2 * A)
        if 0 <= t <= 1:
            out.append((p[0] + t * dx, p[1] + t * dy))
    return out


def concept_c():
    L1, L2, T = (76, 46), (76, 210), (218, 128)
    # closed-eyelid curve: from left edge to the play tip, sagging by `sag`
    A_, B_, sag = (76, 112), T, 18
    mx, my = (A_[0] + B_[0]) / 2, (A_[1] + B_[1]) / 2
    h = math.hypot(B_[0] - A_[0], B_[1] - A_[1]) / 2
    R = (h * h + sag * sag) / (2 * sag)
    nx, ny = (B_[1] - A_[1]) / (2 * h), -(B_[0] - A_[0]) / (2 * h)  # normal pointing up
    c = (mx + nx * (R - sag), my + ny * (R - sag))
    half, sw = 14, 14  # half = gap/2 + rounding stroke/2; stroke rounds corners (concept stage)
    ri, ro = R - half, R + half
    up_i = circle_line(c, ri, L1, T)[-1]
    lf_i = circle_line(c, ri, L1, L2)[-1]
    lo_o = circle_line(c, ro, L2, T)[-1]
    lf_o = circle_line(c, ro, L1, L2)[-1]
    lid = f"M{pt(L1)} L{pt(up_i)} A{F(ri)} {F(ri)} 0 0 1 {pt(lf_i)} Z"
    body = f"M{pt(lf_o)} L{pt(L2)} L{pt(lo_o)} A{F(ro)} {F(ro)} 0 0 1 {pt(lf_o)} Z"
    st = f'stroke="#000" stroke-width="{sw}" stroke-linejoin="round"'
    return svg(f'<g id="symbol" fill="#000" {st}>\n'
               f'  <path id="lid" d="{lid}"/>\n  <path id="play" d="{body}"/>\n</g>')


def lockup(symbol_svg, word, w=820):
    inner = symbol_svg.split("\n", 1)[1].rsplit("</svg>", 1)[0]
    return svg(f'{inner}\n<text x="292" y="166" font-family="Bahnschrift" font-weight="600" '
               f'font-size="118" letter-spacing="-1" fill="#000">{word}</text>', w, 256)


if __name__ == "__main__":
    for k, fn in (("a", concept_a), ("b", concept_b), ("c", concept_c)):
        s = fn()
        open(os.path.join(OUT, f"concept-{k}.svg"), "w").write(s)
        open(os.path.join(OUT, f"concept-{k}-lockup.svg"), "w").write(lockup(s, "freehand"))
        open(os.path.join(OUT, f"concept-{k}-lockup-cap.svg"), "w").write(lockup(s, "Freehand"))
    print("ok")
