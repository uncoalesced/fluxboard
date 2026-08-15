"""Build the FLCT binary prefix trie shipped as keyboard-core/assets/base_dict.bin.

The frequency corpus (count_1w.txt) is web-derived, so it contains common
misspellings as high-frequency entries -- "teh", "hte" and "helo" all outrank or
rival the words they are typos of. A trie built straight from it makes autocorrect
actively wrong: the typo is a valid terminal, so it is never corrected.

The fix is a reference-dictionary intersection at build time, not a runtime
blocklist: only corpus words that also appear in a real spelling dictionary
(SCOWL word lists, or any hunspell/plain wordlist) become terminals.
"""

import argparse
import math
import struct
import sys


def load_reference_words(paths):
    """Read reference wordlists into a lowercase set.

    Accepts plain one-word-per-line lists (SCOWL `final/english-words.*`) and
    hunspell `.dic` files, whose entries carry affix flags after a slash and whose
    first line is an entry count. Possessives and any entry with whitespace are
    dropped: the corpus keys are single lowercase tokens.
    """
    words = set()
    for path in paths:
        # SCOWL ships several files in ISO-8859-1; latin-1 never fails to decode.
        with open(path, "r", encoding="latin-1") as f:
            for line in f:
                entry = line.strip()
                if not entry or entry.startswith("#"):
                    continue
                # hunspell affix flags: "walk/DGS" -> "walk"
                entry = entry.split("/", 1)[0]
                if not entry or " " in entry or "\t" in entry:
                    continue
                words.add(entry.lower())
    return words


# Every English contraction, and the reason this list is written out rather than sourced.
#
# The corpus carries no punctuation at all, so "you're" is not in it -- and its stripped
# form "youre" is then thrown away by the reference intersection, because "youre" is not a
# word either. The result was that *no* contraction reached the dictionary: "don't",
# "it's", "can't" and "i'm" were all absent, which is roughly one word in twenty of running
# English. Typing one produced no suggestion and invited a correction toward something
# else entirely.
#
# Sourcing a list would be the right call for an open-ended set, the way the reference
# wordlists are vendored. Contractions are not one: they are a closed grammatical class of
# a few dozen entries that has not changed in a century, so writing it here is a complete
# statement of it rather than a sample of one.
#
# Each takes the corpus frequency of its stripped form, which is what the corpus actually
# counted. Where that form is also a word in its own right -- "its", "cant", "hell",
# "were" -- the count covers both meanings and both entries get it. That over-states the
# rarer sense, but frequencies are log-normalized into 1-255 before they are stored, which
# compresses a factor of two into about four points. It is not worth a guess at the split.
CONTRACTIONS = (
    "ain't", "aren't", "can't", "couldn't", "didn't", "doesn't", "don't", "hadn't",
    "hasn't", "haven't", "isn't", "mightn't", "mustn't", "needn't", "shan't",
    "shouldn't", "wasn't", "weren't", "won't", "wouldn't",
    "i'm",
    "you're", "we're", "they're",
    "i've", "you've", "we've", "they've", "could've", "should've", "would've",
    "must've",
    "i'll", "you'll", "he'll", "she'll", "it'll", "we'll", "they'll", "that'll",
    "there'll", "who'll",
    "i'd", "you'd", "he'd", "she'd", "it'd", "we'd", "they'd", "that'd", "there'd",
    "who'd",
    "he's", "she's", "it's", "that's", "there's", "here's", "what's", "who's", "let's",
    "where's", "when's", "how's", "why's", "one's", "nobody's", "everyone's",
    "o'clock", "y'all", "ma'am",
)

#: Stripped form -> contraction, e.g. "youre" -> "you're". Built once, used by this script
#: and imported by build_bigrams.py so the two assets agree on the same set.
BARE_TO_CONTRACTION = {c.replace("'", ""): c for c in CONTRACTIONS}


def parse_corpus(words_file, allowed, capture=()):
    """Yield (word, freq) for corpus lines that survive the reference filter.

    `capture` names lowercase forms whose frequency is wanted *whether or not* the
    filter accepts them. That is how contractions get a real weight rather than an
    invented one -- see CONTRACTIONS.
    """
    kept, dropped = 0, 0
    rows = []
    captured = {}
    capture = set(capture)
    with open(words_file, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) < 2:
                continue
            word = parts[0]
            try:
                freq = int(parts[1])
            except ValueError:
                continue
            lowered = word.lower()
            if lowered in capture and freq > captured.get(lowered, 0):
                captured[lowered] = freq
            if allowed is not None and lowered not in allowed:
                dropped += 1
                continue
            kept += 1
            rows.append((word, freq))
    return rows, kept, dropped, captured


def contraction_rows(captured):
    """Rows for every contraction whose stripped form the corpus actually counted."""
    return [
        (contraction, captured[bare])
        for bare, contraction in sorted(BARE_TO_CONTRACTION.items())
        if captured.get(bare)
    ]


def normalize_freq(freq, max_freq):
    """Compress a raw corpus count into the 1-255 byte the binary formats store.

    Logarithmic, because raw web counts span six orders of magnitude and a linear
    squeeze would flatten every word below the top few hundred to 0. Shared with
    build_bigrams.py rather than duplicated: two copies of this curve would drift and
    the two assets would then disagree about what "common" means.
    """
    return max(1, min(255, int((math.log(freq + 1) / math.log(max_freq + 1)) * 255)))


def build_trie(rows):
    class Node:
        def __init__(self):
            self.children = {}
            self.freq = 0
            self.is_terminal = False
            self.offset = -1
            self.char = ""

    root = Node()
    max_freq = max((freq for _, freq in rows), default=0)
    print(f"Max frequency found: {max_freq}")

    for word, freq in rows:
        # Normalize frequency 1-255 logarithmically
        norm_freq = normalize_freq(freq, max_freq)

        current = root
        for char in word:
            if char not in current.children:
                current.children[char] = Node()
                current.children[char].char = char
            current = current.children[char]

        current.is_terminal = True
        if norm_freq > current.freq:
            current.freq = norm_freq

    print("Trie built. Calculating offsets...")

    # Flatten the tree using BFS to optimize locality
    nodes = []
    queue = [root]
    while queue:
        node = queue.pop(0)
        nodes.append(node)
        for char, child in sorted(node.children.items()):
            queue.append(child)

    # Calculate offsets
    current_offset = 4  # 4 bytes for magic header 'FLCT'
    for node in nodes:
        node.offset = current_offset
        child_count = min(255, len(node.children))  # clamp to 255
        node_size = 3 + (child_count * 6)
        current_offset += node_size

    print(f"Total nodes: {len(nodes)}")
    print(
        f"Expected file size: {current_offset} bytes "
        f"({current_offset / 1024 / 1024:.2f} MB)"
    )

    return nodes


def write_flictionary(nodes, output_file):
    print("Writing binary dictionary...")
    with open(output_file, "wb") as f:
        # Magic header
        f.write(b"FLCT")

        for node in nodes:
            children_items = sorted(node.children.items())[:255]  # max 255 children
            child_count = len(children_items)

            # Node header: freq (1B), is_terminal (1B), child_count (1B)
            f.write(
                struct.pack(
                    ">BBB", node.freq, 1 if node.is_terminal else 0, child_count
                )
            )

            # Children: char (2B utf-16be), child_offset (4B int)
            for char, child in children_items:
                # Get UTF-16 encoded char, take first 2 bytes
                char_bytes = char.encode("utf-16-be")
                if len(char_bytes) > 2:
                    char_bytes = char_bytes[:2]
                elif len(char_bytes) < 2:
                    char_bytes = char_bytes.ljust(2, b"\x00")

                f.write(char_bytes)
                f.write(struct.pack(">I", child.offset))

    print(f"Successfully wrote {output_file}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("corpus", help="frequency corpus, e.g. count_1w.txt")
    parser.add_argument("output", help="output .bin path")
    parser.add_argument(
        "--wordlist",
        action="append",
        default=[],
        metavar="PATH",
        help=(
            "reference spelling dictionary to intersect the corpus against; "
            "repeatable. Without it the corpus ships its own typos as valid words."
        ),
    )
    parser.add_argument(
        "--allow-unfiltered",
        action="store_true",
        help="build with no reference wordlist (produces a typo-polluted dictionary)",
    )
    args = parser.parse_args()

    if not args.wordlist and not args.allow_unfiltered:
        parser.error(
            "no --wordlist given. The corpus contains misspellings as "
            "high-frequency entries, which defeats autocorrect. Pass one or more "
            "--wordlist files (see docs/dictionary-pipeline.md), or pass "
            "--allow-unfiltered if you really want the raw corpus."
        )

    allowed = load_reference_words(args.wordlist) if args.wordlist else None
    if allowed is not None:
        print(f"Reference wordlist: {len(allowed)} distinct words")

    rows, kept, dropped, captured = parse_corpus(
        args.corpus, allowed, BARE_TO_CONTRACTION.keys()
    )
    print(f"Corpus words kept: {kept}, dropped as not-a-word: {dropped}")
    if not rows:
        sys.exit("No words survived filtering -- check the wordlist paths.")

    contractions = contraction_rows(captured)
    rows.extend(contractions)
    print(
        f"Contractions added: {len(contractions)} of {len(CONTRACTIONS)} "
        f"(the rest have no corpus frequency to weight them)"
    )

    nodes = build_trie(rows)
    write_flictionary(nodes, args.output)


if __name__ == "__main__":
    main()
