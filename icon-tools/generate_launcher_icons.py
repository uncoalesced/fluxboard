# Engineered by uncoalesced
"""Generate the adaptive launcher icon layers from the FluxBoard brand asset.

Run from the repo root:

    python icon-tools/generate_launcher_icons.py <path-to-flux_logo_beta_transparent.png>

Writes app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/
    ic_launcher_foreground.png
    ic_launcher_monochrome.png

Two things about the source asset are worth knowing before changing anything here.

1. Despite the filename, it is not transparent. About 31% of it is a uniform
   50%-opacity black wash (alpha 128, RGB ~0) -- the background layer was exported
   at half opacity rather than hidden. Dropped in as an adaptive-icon foreground
   that wash covers the whole 108dp canvas and hides the Ink background layer
   entirely, which is the opposite of the intended design. The export is the mark
   composited over a 50% black rectangle, so it inverts exactly:

       a_out = c + 0.5(1 - c)          ->  c = 2*a_out - 1
       colour_out * a_out = mark * c   ->  mark = colour_out * a_out / c

   a_out == 0.5 gives c == 0 (pure wash, fully transparent); a_out == 1 leaves the
   mark untouched.

2. The mark is a circular disc, so it is sized to a 72dp diameter on the 108dp
   canvas. That exactly fills a circular launcher mask while still leaving the Ink
   background visible at the corners of squircle masks.

The monochrome layer is luminance-keyed rather than alpha-masked. The logo has
genuine black line work inside a white disc; masking on alpha alone would flatten
it into a solid blob and lose the keyboard detail entirely.
"""

import os
import sys

from PIL import Image

# 108dp canvas per bucket, artwork confined to a 72dp-diameter disc.
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SAFE_FRACTION = 72.0 / 108.0

# alpha <= 129/255 is the 50% wash; anything above it is real mark coverage.
WASH_COVERAGE_CUTOFF = 0.012


def recover_mark(source_path):
    """Undo the 50%-black composite and crop to the mark's real bounds."""
    image = Image.open(source_path).convert("RGBA")
    width, height = image.size
    src = image.load()

    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    dst = out.load()

    for y in range(height):
        for x in range(width):
            r, g, b, a = src[x, y]
            alpha = a / 255.0
            coverage = 2.0 * alpha - 1.0
            if coverage <= WASH_COVERAGE_CUTOFF:
                dst[x, y] = (0, 0, 0, 0)
            else:
                scale = alpha / coverage
                dst[x, y] = (
                    min(255, int(round(r * scale))),
                    min(255, int(round(g * scale))),
                    min(255, int(round(b * scale))),
                    min(255, int(round(coverage * 255))),
                )

    bbox = out.getbbox()
    if bbox is None:
        sys.exit("Nothing survived wash removal -- is this the right asset?")
    return out.crop(bbox)


def monochrome(art):
    """White silhouette whose alpha follows luminance, keeping internal detail."""
    src = art.load()
    result = Image.new("RGBA", art.size, (255, 255, 255, 0))
    dst = result.load()
    for y in range(art.size[1]):
        for x in range(art.size[0]):
            r, g, b, a = src[x, y]
            lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
            dst[x, y] = (255, 255, 255, int(round(a * lum)))
    return result


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    source = sys.argv[1]
    res_dir = os.path.join("app", "src", "main", "res")
    if not os.path.isdir(res_dir):
        sys.exit(f"{res_dir} not found -- run this from the repo root.")

    mark = recover_mark(source)
    print(f"recovered mark: {mark.size[0]}x{mark.size[1]}")

    for bucket, canvas in DENSITIES.items():
        target = int(round(canvas * SAFE_FRACTION))
        art = mark.resize((target, target), Image.LANCZOS)
        offset = (canvas - target) // 2

        out_dir = os.path.join(res_dir, f"drawable-{bucket}")
        os.makedirs(out_dir, exist_ok=True)

        foreground = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        foreground.paste(art, (offset, offset), art)
        fg_path = os.path.join(out_dir, "ic_launcher_foreground.png")
        foreground.save(fg_path, "PNG", optimize=True)

        mono_art = monochrome(art)
        mono = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        mono.paste(mono_art, (offset, offset), mono_art)
        mono_path = os.path.join(out_dir, "ic_launcher_monochrome.png")
        mono.save(mono_path, "PNG", optimize=True)

        print(
            f"  {bucket:<8} canvas {canvas:>3}  disc {target:>3}  "
            f"fg {os.path.getsize(fg_path):>6}B  mono {os.path.getsize(mono_path):>6}B"
        )


if __name__ == "__main__":
    main()
