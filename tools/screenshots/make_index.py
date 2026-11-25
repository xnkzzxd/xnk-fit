#!/usr/bin/env python3
"""Builds index.html and index.md from the PNGs in a directory: for each screen a table, rows = light/dark and font
scale, columns = phone / tablet. Names: <screen>_<hp|tablet>_<terang|gelap>_<scale>.png."""
import html, re, sys
from pathlib import Path

ORDER = ["onboarding", "skrining", "hari-ini", "latihan", "logger", "makan", "planner", "saya", "kartu", "pengaturan", "bantuan", "privasi", "data"]
out = Path(sys.argv[1])
pat = re.compile(r"^(?P<screen>.+)_(?P<dev>hp|tablet)_(?P<theme>terang|gelap)_(?P<scale>[0-9.]+)\.png$")
shots = {}
for f in sorted(out.glob("*.png")):
    m = pat.match(f.name)
    if m:
        shots[(m["screen"], m["theme"], m["scale"], m["dev"])] = f.name
screens = sorted({k[0] for k in shots}, key=lambda s: (ORDER.index(s) if s in ORDER else 99, s))
rows = [(t, sc) for t in ("terang", "gelap") for sc in ("1.0", "1.3")]
missing = [s for s in ORDER if s not in screens]

h = ["<!doctype html><meta charset=utf-8><title>XNK FIT screenshots</title>",
     "<style>body{font-family:sans-serif;margin:16px}td{vertical-align:top;padding:4px}img{max-width:100%;border:1px solid #888}"
     "table{border-collapse:collapse;width:100%}th{text-align:left}.hp img{max-height:520px}.tablet img{max-height:520px}</style>",
     "<h1>XNK FIT screenshots</h1>"]
md = ["# XNK FIT screenshots", ""]
if missing:
    h.append("<p><b>Missing screens:</b> " + ", ".join(missing) + "</p>"); md += ["**Missing screens:** " + ", ".join(missing), ""]
for s in screens:
    h.append(f"<h2 id='{s}'>{html.escape(s)}</h2><table><tr><th>variant<th>phone (hp)<th>tablet")
    md += [f"## {s}", "", "| variant | phone | tablet |", "| --- | --- | --- |"]
    for t, sc in rows:
        cells, mcells = [], []
        for d in ("hp", "tablet"):
            n = shots.get((s, t, sc, d))
            cells.append(f"<a href='{n}'><img class={d} src='{n}' loading=lazy></a>" if n else "<i>missing</i>")
            mcells.append(f"![{n}]({n})" if n else "missing")
        h.append(f"<tr><td>{t}, font {sc}<td>{cells[0]}<td>{cells[1]}")
        md.append(f"| {t}, font {sc} | {mcells[0]} | {mcells[1]} |")
    h.append("</table>"); md.append("")
(out / "index.html").write_text("\n".join(h), encoding="utf-8")
(out / "index.md").write_text("\n".join(md), encoding="utf-8")
print(f"{len(shots)} images, {len(screens)} screens, missing: {missing or 'none'}")
