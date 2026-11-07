"""Export the approved SVG mark into Android launcher resources.

Requires Pillow (see requirements.txt). The SVG uses solid, even-odd vector
paths on an adaptive 108dp artboard; its 72dp viewBox is the visible icon area.
"""

from pathlib import Path
import math
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "ping/src/main/res"
SVG = ROOT / "artwork/app-icon.svg"
NS = {"svg": "http://www.w3.org/2000/svg"}
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def rings(path):
    """Flatten absolute SVG M/L/C/Z commands for bitmap export only.

    Android receives the original cubic paths, not this raster approximation.
    """
    if re.sub(r"[MLCZ\d.,\s-]", "", path):
        raise ValueError("Launcher SVG paths must use absolute M/L/C/Z commands")
    result, ring = [], []
    for part in re.split(r"(?=[MLCZ])", path):
        if not part.strip():
            continue
        command = part[0]
        points = [tuple(map(float, pair.split(","))) for pair in re.findall(r"[-\d.]+,[-\d.]+", part)]
        if command == "M":
            if ring or len(points) != 1:
                raise ValueError("Each closed contour must start with one move")
            ring = points
        elif command == "L":
            ring.extend(points)
        elif command == "C":
            if not ring or len(points) != 3:
                raise ValueError("A cubic segment requires two controls and an endpoint")
            a, b, c, d = ring[-1], *points
            steps = max(2, math.ceil((math.dist(a, b) + math.dist(b, c) + math.dist(c, d)) / 0.1))
            for step in range(1, steps + 1):
                t = step / steps
                ring.append(tuple((1-t)**3*a[i] + 3*(1-t)**2*t*b[i] + 3*(1-t)*t*t*c[i] + t**3*d[i]
                                  for i in (0, 1)))
        elif command == "Z":
            result.append(ring)
            ring = []
    if ring:
        raise ValueError("Launcher contours must be closed")
    return result


def render(size, background, paths, round_icon=False):
    # Supersample both the outline and circular mask to keep small icons crisp.
    scale = 4
    canvas = Image.new("RGBA", (size * scale, size * scale), background)
    for path in paths:
        mask = Image.new("L", canvas.size)
        for ring in rings(path["d"]):
            part = Image.new("L", canvas.size)
            points = [((x - 18) * size * scale / 72, (y - 18) * size * scale / 72) for x, y in ring]
            ImageDraw.Draw(part).polygon(points, fill=255)
            mask = ImageChops.difference(mask, part)
        canvas.paste(path["fill"], (0, 0), mask)
    if round_icon:
        circle = Image.new("L", canvas.size)
        ImageDraw.Draw(circle).ellipse((0, 0, canvas.width - 1, canvas.height - 1), fill=255)
        canvas.putalpha(circle)
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


def main():
    svg = ET.parse(SVG).getroot()
    if svg.get("viewBox") != "18 18 72 72":
        raise ValueError("Expected the central 72dp viewBox of a 108dp adaptive icon")
    background = svg.find("svg:rect[@id='background']", NS).get("fill")
    paths = [node.attrib for node in svg.findall("svg:path", NS)]
    if not paths:
        raise ValueError("Launcher mark is empty")
    for path in paths:
        if path.get("fill-rule") != "evenodd":
            raise ValueError("Launcher mark paths must use even-odd filling")
        points = [tuple(map(float, pair.split(","))) for pair in re.findall(r"[-\d.]+,[-\d.]+", path["d"])]
        if any((x - 54) ** 2 + (y - 54) ** 2 > 30 ** 2 for x, y in points):
            raise ValueError("Mark exceeds its circular adaptive safe area")

    foreground = ['<?xml version="1.0" encoding="utf-8"?>',
                  '<!-- Generated from artwork/app-icon.svg by tools/icons/generate.py. -->',
                  '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
                  '    android:width="108dp" android:height="108dp"',
                  '    android:viewportWidth="108" android:viewportHeight="108">']
    for path in paths:
        foreground.append(f'    <path android:fillColor="{path["fill"]}" android:fillType="evenOdd"')
        foreground.append(f'        android:pathData="{path["d"]}" />')
    foreground.append('</vector>')
    (RES / "drawable/ic_launcher_foreground.xml").write_bytes(("\n".join(foreground) + "\n").encode())
    (RES / "drawable/ic_launcher_background.xml").write_bytes((
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Generated from artwork/app-icon.svg by tools/icons/generate.py. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n'
        f'    <path android:fillColor="{background}" android:pathData="M0,0h108v108h-108z" />\n'
        '</vector>\n').encode())

    for density, size in DENSITIES.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(exist_ok=True)
        render(size, background, paths).save(folder / "ic_launcher.png", optimize=True)
        render(size, background, paths, round_icon=True).save(folder / "ic_launcher_round.png", optimize=True)


if __name__ == "__main__":
    main()
