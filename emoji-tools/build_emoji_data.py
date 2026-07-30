# Engineered by uncoalesced
"""Generate the picker's emoji dataset from Unicode's emoji-test.txt.

Run from the repo root:

    python emoji-tools/build_emoji_data.py

Reads emoji-tools/emoji-test.txt (vendored) and writes
keyboard-core/src/main/assets/emoji_data.txt.

Why this exists at all
----------------------
Android renders emoji natively from its own system font, so the app bundles no
emoji artwork. What Android does *not* expose to a third-party app is the
*categorisation*: there is no public API to ask "which emoji are Food & Drink"
or "what is this codepoint called". That metadata has to be shipped, and
Unicode's emoji-test.txt (UTS #51) is the canonical source for it -- the same
file every keyboard's picker is ultimately derived from.

This is a static asset generated offline, exactly like the SCOWL-filtered
dictionary. Nothing here is fetched at runtime.

What gets filtered out, and why
-------------------------------
* `component`, `minimally-qualified` and `unqualified` rows. Only
  fully-qualified sequences are what a keyboard should emit.
* The Component group entirely (skin-tone swatches, hair components). They are
  modifiers, not emoji anyone picks on their own.
* Skin-tone variant sequences (U+1F3FB..U+1F3FF). Including them takes the set
  from 1,906 to 3,781 and fills the grid with five near-identical copies of
  every person. Base emoji only; per-emoji tone selection is a separate feature
  and belongs on a long-press, not in the main grid.

Each row keeps its emoji version (E0.6, E15.1, ...) because the device's system
font decides what actually renders. An emoji newer than the platform draws as a
tofu box, so the runtime filters by version rather than showing broken glyphs.

Output format, one record per line, tab-separated:

    <emoji>\t<emoji version>\t<name>

with group headers on their own line as `#<group name>`. Deliberately not JSON:
this is parsed on the IME's startup path, and a line split is markedly cheaper
than instantiating a JSON tree for ~1,900 records.
"""

import os
import re
import sys

SOURCE = os.path.join("emoji-tools", "emoji-test.txt")
OUTPUT = os.path.join("keyboard-core", "src", "main", "assets", "emoji_data.txt")

# Modifiers, not standalone emoji.
SKIN_TONES = {0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF}

# Swatches and hair components; never shown as pickable emoji.
SKIPPED_GROUPS = {"Component"}

DATA_LINE = re.compile(
    r"^(?P<codes>[0-9A-Fa-f ]+);\s*(?P<status>[a-z-]+)\s*#\s*(?P<glyph>\S+)\s+"
    r"E(?P<version>[0-9.]+)\s+(?P<name>.+)$"
)


def parse(path):
    """Yield (group, glyph, version, name) for every pickable emoji."""
    group = None
    with open(path, encoding="utf-8") as handle:
        for raw in handle:
            line = raw.rstrip("\n")
            if line.startswith("# group:"):
                group = line.split(":", 1)[1].strip()
                continue
            if not line or line.startswith("#"):
                continue

            match = DATA_LINE.match(line)
            if match is None:
                continue
            if match.group("status") != "fully-qualified":
                continue
            if group in SKIPPED_GROUPS:
                continue

            codes = [int(c, 16) for c in match.group("codes").split()]
            if any(c in SKIN_TONES for c in codes):
                continue

            yield group, match.group("glyph"), match.group("version"), match.group("name")


def main():
    if not os.path.isfile(SOURCE):
        sys.exit(f"{SOURCE} not found -- run this from the repo root.")

    by_group = {}
    order = []
    for group, glyph, version, name in parse(SOURCE):
        if group not in by_group:
            by_group[group] = []
            order.append(group)
        by_group[group].append((glyph, version, name))

    total = 0
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8", newline="\n") as out:
        for group in order:
            out.write(f"#{group}\n")
            for glyph, version, name in by_group[group]:
                out.write(f"{glyph}\t{version}\t{name}\n")
                total += 1

    print(f"wrote {OUTPUT}")
    for group in order:
        print(f"  {group:<20} {len(by_group[group]):>5}")
    print(f"  {'TOTAL':<20} {total:>5}")
    print(f"  size {os.path.getsize(OUTPUT) / 1024:.1f} KB")


if __name__ == "__main__":
    main()
