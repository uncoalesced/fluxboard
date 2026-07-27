# Base Dictionary: Format, Provenance & Regeneration (Phase 18)

This document covers `keyboard-core/src/main/assets/base_dict.bin` -- the base
dictionary the predictive text engine (`PredictionEngine.kt`) reads -- and the
offline pipeline that produces it, which now lives in `dictionary-tools/`.

## The pipeline (dictionary-tools/)

Phase 18's offline preprocessing pipeline exists in-repo and is reproducible:

| File | Role |
|---|---|
| `dictionary-tools/build_flictionary.py` | Builds the trie binary from a `word<TAB>frequency` corpus |
| `dictionary-tools/count_1w.txt` | The frequency corpus (333,333 entries) |
| `dictionary-tools/scowl/` | SCOWL reference word lists, sizes 10-60 (the spelling filter) |
| `dictionary-tools/base_dict.bin` | The generated binary |

Regenerate with (from the repo root, `bash`):

```bash
python dictionary-tools/build_flictionary.py dictionary-tools/count_1w.txt dictionary-tools/base_dict.bin $(for f in dictionary-tools/scowl/*-words.*; do printf -- '--wordlist %s ' "$f"; done)
```

The output is **byte-for-byte identical** to the shipped asset
(`keyboard-core/src/main/assets/base_dict.bin`) -- both files have SHA-256
`daddfb001a00c8694602fbf09f3e8a4c5d8377e6318f338ef661632b03a98d35`. The binary
is therefore reproducible from the committed inputs, not an unexplained blob.
(Per project rules, this tooling is Python and never ships in the APK.)

`--wordlist` is **required**; the script refuses to run without one unless
`--allow-unfiltered` is passed explicitly. That is deliberate -- see the
data-quality section below for what an unfiltered build does to autocorrect.

## Provenance & license

- **Source wordlist:** `count_1w.txt` is the Google Web Trillion Word Corpus
  unigram frequency list published by Peter Norvig (norvig.com/ngrams),
  derived from the Google Web 1T dataset -- its signature first line is
  `the<TAB>23135851162`. This resolves the earlier open question: the data is
  **not** derived from FlorisBoard's dictionaries. Only the *binary container
  format* (the `FLCT` "Flictionary" trie, below) follows FlorisBoard's concept.
- **Action item (still open):** confirm and record the redistribution terms of
  `count_1w.txt` in third-party notices before release. Norvig publishes the
  ngrams data files as freely usable, but the underlying Google Web 1T corpus
  has its own terms; this should be pinned down explicitly rather than assumed.

## Binary format (as read by `PredictionEngine.kt` and written by the script)

A serialized prefix trie, 7,253,251 bytes, mapped read-only at runtime via
`MappedByteBuffer`. The root node sits at offset 4 and has 26 children (`a`-`z`).

| Offset | Size | Meaning |
|---|---|---|
| 0 | 4 bytes | Magic header, ASCII `FLCT` |
| 4 | -- | Root trie node |

Each node:

| Field | Size | Meaning |
|---|---|---|
| frequency | u8 | Word frequency, 0-255 (only meaningful when terminal) |
| isTerminal | u8 | 1 if a word ends at this node |
| childCount | u8 | Number of children (clamped to 255) |
| children | childCount x 6 bytes | Per child: character as u16 big-endian, then absolute node offset as i32 big-endian |

Nodes are laid out BFS order for locality; frequencies are normalized from the
raw corpus counts on a logarithmic scale so the most common word maps to 255.

## The spelling filter (why the corpus alone is not usable)

`count_1w.txt` is a raw web corpus, so it carries common misspellings as
high-frequency "words". Built verbatim, they became terminal trie entries and
**silently disabled autocorrect for exactly the typos users make most**: the
engine will not correct a word that the dictionary says is already a word.

| Entry | Raw count | Freq in old binary | Note |
|---|---|---|---|
| the | 23,135,851,162 | 255 | correct |
| that | 3,400,031,103 | 234 | correct |
| teh | 1,688,205 | 153 | junk -- typo of "the" |
| helo | 623,481 | 142 | junk -- typo of "hello" |
| hte | 301,534 | 134 | junk -- typo of "the" |
| thw | 144,797 | 126 | junk -- typo of "the" |
| wrod | 61,147 | 117 | junk -- typo of "word" |

A relative scoring rule in `PredictionEngine` (the typed word competes as its
own distance-0 candidate, and a correction must beat it) recovered the *dominated*
cases like `thw -> the`, but self-defending junk such as `teh` at frequency 153
still blocked its own correction. Scoring cannot fix a bad dictionary.

**The fix is the build-time intersection**, not a runtime blocklist:
`build_flictionary.py` keeps a corpus word only if it also appears in a real
spelling dictionary. The shipped build intersects against SCOWL sizes 10-60
(`dictionary-tools/scowl/`), size 60 being SCOWL's own "normal dictionary" cut.

Result of that intersection:

| Metric | Before | After |
|---|---|---|
| Corpus words accepted | 333,333 | 59,637 (273,696 dropped) |
| Binary size | 7,253,251 B (6.9 MB) | 1,291,759 B (1.23 MB) |
| `teh` / `hte` / `helo` / `adn` / `recieve` | terminal, freq 134-153 | absent from the trie |
| `the` / `hello` / `and` / `receive` | terminal | terminal, same frequencies |

The reference lists are **vendored** rather than downloaded at build time so the
binary stays reproducible if the upstream mirror goes away.

Verify after any rebuild:

1. The known-junk rows above must be absent from the new binary.
2. No real word may be clobbered -- spot-check `the`, `hello`, `and`, `receive`
   keep their frequencies.
3. Re-run `:keyboard-core:testDebugUnitTest --tests "*PredictionEngine*" --rerun`.

## Provenance & license -- SCOWL

`dictionary-tools/scowl/` holds sizes 10, 20, 35, 40, 50, 55 and 60 of both
`english-words.*` and `american-words.*` (about 1.0 MB total), taken from the
SCOWL (Spell Checker Oriented Word Lists) distribution by Kevin Atkinson, via the
`rdeits/SCOWL-mirror` copy of the `final/` directory. SCOWL's own copyright
notice is vendored alongside them as `scowl/SCOWL-Copyright.txt`: the core lists
are public domain or BSD-style permissive, with attribution required, which this
section provides. These files are build-time inputs only and never ship in the
APK.

## Size budget

The filtered binary is ~1.23 MB inside the APK assets, down from ~6.9 MB -- a
5.7 MB reduction against the 100 MB installed-size budget (Phase 32's CI check
measures the release APK).
