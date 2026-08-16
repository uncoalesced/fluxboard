# Engineered by uncoalesced
"""Build the FLBG bigram table shipped as keyboard-core/assets/bigram_lm.bin.

Answers one question at runtime: given the word the user just finished, which words
plausibly come next? That is the signal both autocorrect and the glide decoder were
missing -- both ranked candidates with no idea what sentence they were in.

Built from Norvig's count_2w.txt, the bigram companion to the count_1w.txt that
base_dict.bin already uses. Same corpus family, so the same caveat applies and the
same fix is used: the web-derived counts contain misspellings as high-frequency
entries, so a pair is only kept when *both* of its words survive the SCOWL
intersection that keeps base_dict.bin typo-free. `load_reference_words` is imported
from build_flictionary rather than copied, so there is one filter and it cannot drift.

Two caps keep the asset bounded without losing the cases that matter. Only the
N_PREV_WORDS most frequent previous-words get a record at all (sentence context is
overwhelmingly about common words), and each keeps at most MAX_FOLLOWERS next-words.

Format (all integers big-endian):

    [4]  "FLBG"
    [4]  u32 recordCount
    recordCount records, sorted lexicographically by prevWord:
      [1]              prevWordLen
      [prevWordLen]    prevWord, UTF-8
      [1]              followerCount (<= MAX_FOLLOWERS)
      followerCount followers, sorted by weight descending:
        [1]            nextWordLen
        [nextWordLen]  nextWord, UTF-8
        [1]            weight, 1-255
"""

import argparse
import struct
import sys

from build_flictionary import (
    BARE_TO_CONTRACTION,
    CONTRACTIONS,
    load_reference_words,
    normalize_freq,
)

CONTRACTION_SET = frozenset(CONTRACTIONS)


def canonical(word, allowed):
    """Rewrite a stripped contraction the corpus counted into the spelling users type.

    The corpus carries no punctuation, so "dont know" is what it counted while "don't"
    is what the keyboard looks up. Without this the pair is dropped outright, because
    "dont" fails the reference intersection.

    Deliberately not applied when the stripped form is a word in its own right --
    "its", "cant", "were", "hell". There the count belongs to the plain word at least
    as much as to the contraction, and rewriting would lose the plain word's context
    entirely. Those cases are covered at runtime instead, where `boostFor` retries an
    apostrophe-stripped lookup.
    """
    if word in allowed:
        return word
    return BARE_TO_CONTRACTION.get(word, word)


def acceptable(word, allowed):
    """A word may stand in a pair if it is a reference word or a known contraction."""
    return word in allowed or word in CONTRACTION_SET

# Vocabulary cap on the *previous* word. A bounded structure covers the real cases:
# context matters most after common words, and the tail costs bytes for pairs a user
# will effectively never type.
N_PREV_WORDS = 60_000

# Fan-out cap per previous word. Beyond the top handful the weights are noise, and the
# runtime lookup is a linear scan over this list.
MAX_FOLLOWERS = 8

# A length prefix is one byte, so nothing longer can be represented. Anything near this
# is corpus junk rather than a word.
MAX_WORD_BYTES = 255


def load_unigram_ranks(path, allowed, limit):
    """The `limit` most frequent corpus words that survive the reference filter.

    Returned as a set, because rank itself is not stored -- it only decides which
    previous-words earn a record.
    """
    rows = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) < 2:
                continue
            word = canonical(parts[0].lower(), allowed)
            if not acceptable(word, allowed):
                continue
            try:
                rows.append((word, int(parts[1])))
            except ValueError:
                continue
    rows.sort(key=lambda r: r[1], reverse=True)
    return {word for word, _ in rows[:limit]}


def parse_bigrams(path, allowed, prev_allowed):
    """Yield surviving (prevWord, nextWord, count) triples from count_2w.txt.

    The line format is `"word1 word2\\tcount"` -- tab-separated, unlike count_1w.txt
    which is space-separated. Splitting on whitespace generally would put the count in
    the wrong column for nothing and silently produce zero usable rows.
    """
    kept, dropped = 0, 0
    pairs = []
    with open(path, "r", encoding="latin-1") as f:
        for line in f:
            line = line.rstrip("\n")
            if "\t" not in line:
                continue
            pair, _, count_text = line.partition("\t")
            words = pair.split()
            if len(words) != 2:
                continue
            first = canonical(words[0].lower(), allowed)
            second = canonical(words[1].lower(), allowed)
            try:
                count = int(count_text.strip())
            except ValueError:
                continue
            if first not in prev_allowed or not acceptable(second, allowed):
                dropped += 1
                continue
            if (
                len(first.encode("utf-8")) > MAX_WORD_BYTES
                or len(second.encode("utf-8")) > MAX_WORD_BYTES
            ):
                dropped += 1
                continue
            kept += 1
            pairs.append((first, second, count))
    return pairs, kept, dropped


def build_table(pairs):
    """Collapse triples into {prevWord: [(nextWord, weight)]}, capped and normalized."""
    grouped = {}
    for first, second, count in pairs:
        followers = grouped.setdefault(first, {})
        # The corpus is case-folded into this map, so the same pair can arrive twice.
        followers[second] = max(followers.get(second, 0), count)

    table = {}
    for first, followers in grouped.items():
        top = sorted(followers.items(), key=lambda kv: kv[1], reverse=True)
        top = top[:MAX_FOLLOWERS]
        # Normalized against this previous-word's own best follower, not against a
        # global maximum. The global bigram count spans to ~2.8e9, and normalize_freq
        # is logarithmic, so a global denominator compresses every surviving pair into
        # roughly 100-214 -- "thank you" and "thank him" would differ by less than the
        # noise, and the stored weight would degenerate into a flag meaning "this pair
        # exists". Per-word it answers the question the runtime actually asks: among
        # the words that follow this one, how strongly does this candidate?
        local_max = top[0][1]
        table[first] = [(w, normalize_freq(c, local_max)) for w, c in top]
    return table


def write_flbg(table, output_file):
    print("Writing bigram table...")
    with open(output_file, "wb") as f:
        f.write(b"FLBG")
        f.write(struct.pack(">I", len(table)))
        # Lexicographic order so a future reader can binary-search the records without
        # the format changing. Nothing does today -- the runtime parses it whole.
        for prev_word in sorted(table.keys()):
            followers = table[prev_word]
            prev_bytes = prev_word.encode("utf-8")
            f.write(struct.pack(">B", len(prev_bytes)))
            f.write(prev_bytes)
            f.write(struct.pack(">B", len(followers)))
            for next_word, weight in followers:
                next_bytes = next_word.encode("utf-8")
                f.write(struct.pack(">B", len(next_bytes)))
                f.write(next_bytes)
                f.write(struct.pack(">B", weight))
    print(f"Successfully wrote {output_file}")


def report_samples(table, samples):
    """Eyeball check. Noise here is the tab-vs-space parsing bug, first suspect."""
    for word in samples:
        followers = table.get(word)
        if not followers:
            print(f"  {word!r}: (no record)")
            continue
        rendered = ", ".join(f"{w}({s})" for w, s in followers)
        print(f"  {word!r}: {rendered}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("corpus", help="bigram corpus, e.g. count_2w.txt")
    parser.add_argument("output", help="output .bin path")
    parser.add_argument(
        "--unigrams",
        default="dictionary-tools/count_1w.txt",
        help="unigram corpus used to rank which previous-words earn a record",
    )
    parser.add_argument(
        "--wordlist",
        action="append",
        default=[],
        metavar="PATH",
        help=(
            "reference spelling dictionary to intersect the corpus against; "
            "repeatable. Required for the same reason build_flictionary.py "
            "requires it: the corpus ships misspellings as frequent entries."
        ),
    )
    args = parser.parse_args()

    if not args.wordlist:
        parser.error(
            "no --wordlist given. Both halves of a bigram must survive the same "
            "SCOWL intersection that keeps base_dict.bin typo-free, or context "
            "ranking will promote the corpus's own misspellings."
        )

    allowed = load_reference_words(args.wordlist)
    print(f"Reference wordlist: {len(allowed)} distinct words")

    prev_allowed = load_unigram_ranks(args.unigrams, allowed, N_PREV_WORDS)
    print(f"Previous-word vocabulary: {len(prev_allowed)} (cap {N_PREV_WORDS})")

    pairs, kept, dropped = parse_bigrams(args.corpus, allowed, prev_allowed)
    print(f"Bigrams kept: {kept}, dropped: {dropped}")
    if not pairs:
        sys.exit(
            "No bigrams survived filtering. If the corpus is Norvig's count_2w.txt "
            "the first suspect is the tab-vs-space column split, not the wordlist."
        )

    table = build_table(pairs)
    print(f"Distinct previous-words with a record: {len(table)}")
    print("Sample followers:")
    report_samples(table, ["thank", "how", "i", "going"])

    write_flbg(table, args.output)


if __name__ == "__main__":
    main()
