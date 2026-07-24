# Base Dictionary: Format, Provenance & Regeneration (Phase 18)

This document covers `keyboard-core/src/main/assets/base_dict.bin` -- the base
dictionary the predictive text engine (`PredictionEngine.kt`) reads -- and the
offline pipeline that produces it, which now lives in `dictionary-tools/`.

## The pipeline (dictionary-tools/)

Phase 18's offline preprocessing pipeline exists in-repo and is reproducible:

| File | Role |
|---|---|
| `dictionary-tools/build_flictionary.py` | Builds the trie binary from a `word<TAB>frequency` corpus |
| `dictionary-tools/count_1w.txt` | The source wordlist (333,333 entries) |
| `dictionary-tools/base_dict.bin` | The generated binary |

Regenerate with:

```
python dictionary-tools/build_flictionary.py dictionary-tools/count_1w.txt dictionary-tools/base_dict.bin
```

The output is **byte-for-byte identical** to the shipped asset
(`keyboard-core/src/main/assets/base_dict.bin`) -- both files have SHA-256
`d9aaa2b80657cfa62faa48c13aa31398fb9020316194376255337ecf7629cc61`. The binary
is therefore reproducible from the committed inputs, not an unexplained blob.
(Per project rules, this tooling is Python and never ships in the APK.)

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

## Known data-quality problem: junk terminal entries

The pipeline currently does **no filtering** -- `build_flictionary.py` inserts
every `word freq` line from `count_1w.txt` verbatim. Because the source is a raw
web corpus, it contains common misspellings as high-frequency "words", and they
flow straight into the dictionary as terminal entries:

| Entry | In count_1w.txt (raw count) | Normalized freq in binary | Note |
|---|---|---|---|
| the | 23,135,851,162 | 255 | correct |
| that | 3,400,031,103 | 234 | correct |
| teh | 1,688,205 | 153 | junk -- typo of "the" |
| helo | 623,481 | 142 | junk -- typo of "hello" |
| hte | 301,534 | 134 | junk -- typo of "the" |
| thw | 144,797 | 126 | junk -- typo of "the" |
| wrod | 61,147 | 117 | junk -- typo of "word" |

Impact: autocorrect originally refused to correct any typed word that existed
in the dictionary, so these junk entries silently disabled correction for
exactly the typos users make most. The engine now uses a relative rule (the
typed word competes as its own distance-0 candidate and a correction must beat
it), which restores correction for dominated junk like "thw" -> "the" -- but
self-defending junk with a high frequency (e.g. "teh" at 153) still blocks its
own correction.

**The durable fix is in the pipeline, not the engine:** add a filtering step to
`build_flictionary.py` before insertion. Recommended filters:

1. Restrict to lowercase `a-z` tokens, length 2-30 (drop fragments and symbols).
2. Remove entries below a frequency floor, and/or intersect the corpus list
   against a curated valid-word list (e.g. a spellcheck dictionary such as
   SCOWL/`hunspell`, permissively licensed) so web-corpus typos are dropped
   while real low-frequency words are kept.
3. After rebuilding, re-run `PredictionEngineTest.testAutoCorrection` -- the
   `thw -> the` assertion must pass and no real word may be clobbered -- and
   confirm the known-junk rows above are absent from the new binary.

## Size budget

Current binary is ~7.3 MB inside the APK assets; track any regenerated
dictionary against the 100 MB installed-size budget (Phase 32's CI check
measures the release APK).
