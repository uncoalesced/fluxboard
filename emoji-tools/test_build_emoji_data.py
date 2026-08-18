# Engineered by uncoalesced
"""Self-check for build_emoji_data.parse_lines.

Run it directly, no framework:

    python emoji-tools/test_build_emoji_data.py

Plain asserts on purpose. This pipeline runs offline, by hand, a few times a year;
adding pytest to the project so four filtering rules can be checked would cost more
than it saves. What it protects is real, though: every rule below silently changes
what ships in the emoji picker, and the only other way to notice is to look at a
phone and count.

No emoji literals -- scripts/check-source-rules.sh bans them across all source
including test fixtures, so glyphs are built from their codepoints.
"""

import sys

from build_emoji_data import parse_lines

GRIN = chr(0x1F600)
WAVE = chr(0x1F44B)
LIGHT_SKIN = chr(0x1F3FB)


def records(text):
    return list(parse_lines(text.splitlines()))


def test_group_and_fields():
    out = records(
        f"# group: Smileys & Emotion\n"
        f"1F600 ; fully-qualified # {GRIN} E1.0 grinning face\n"
    )
    assert out == [("Smileys & Emotion", GRIN, "1.0", "grinning face")], out


def test_only_fully_qualified_survives():
    # minimally-qualified and unqualified sequences render inconsistently across
    # fonts; a keyboard should only ever emit the canonical form.
    out = records(
        f"# group: Smileys & Emotion\n"
        f"1F600 ; minimally-qualified # {GRIN} E1.0 grinning face\n"
        f"1F600 ; unqualified # {GRIN} E1.0 grinning face\n"
        f"1F600 ; component # {GRIN} E1.0 grinning face\n"
    )
    assert out == [], out


def test_component_group_is_dropped_whole():
    # Skin-tone swatches and hair components are modifiers. Nobody picks one alone.
    out = records(
        f"# group: Component\n"
        f"1F3FB ; fully-qualified # {LIGHT_SKIN} E1.0 light skin tone\n"
    )
    assert out == [], out


def test_skin_tone_variants_are_dropped_from_real_groups():
    # The reason the set is 1,906 and not 3,781: five near-identical copies of
    # every person is not a picker, it is a wall.
    out = records(
        f"# group: People & Body\n"
        f"1F44B ; fully-qualified # {WAVE} E0.6 waving hand\n"
        f"1F44B 1F3FB ; fully-qualified # {WAVE}{LIGHT_SKIN} E1.0 waving hand: light skin tone\n"
    )
    assert [r[1] for r in out] == [WAVE], out


def test_comments_and_blanks_do_not_end_a_group():
    # emoji-test.txt carries subgroup headers and counts as ordinary comments in
    # the middle of a group. Treating one as a group boundary would split every
    # category into fragments.
    out = records(
        f"# group: Smileys & Emotion\n"
        f"\n"
        f"# subgroup: face-smiling\n"
        f"1F600 ; fully-qualified # {GRIN} E1.0 grinning face\n"
        f"# 9 codes\n"
        f"1F44B ; fully-qualified # {WAVE} E0.6 waving hand\n"
    )
    assert [r[0] for r in out] == ["Smileys & Emotion"] * 2, out


def test_records_before_any_group_header_carry_no_group():
    # Not a shape the real file has; asserted so a truncated source produces a
    # visible None rather than quietly attaching rows to whatever came last.
    out = records(f"1F600 ; fully-qualified # {GRIN} E1.0 grinning face\n")
    assert out == [(None, GRIN, "1.0", "grinning face")], out


def main():
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for test in tests:
        test()
    print(f"ok - {len(tests)} tests")


if __name__ == "__main__":
    sys.exit(main())
