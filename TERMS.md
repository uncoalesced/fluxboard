# Terms and conditions

Last updated 10 August 2026. Applies to FluxBoard `v0.1.5-BETA` and later.

FluxBoard is free MIT-licensed software with no accounts and no service behind it,
so most of what a normal terms document exists to cover does not apply here.
There is nothing to subscribe to, nothing to cancel, and no data of yours held
anywhere we could withhold.

What follows is short on purpose.

## The licence is the real agreement

The software is provided under the [MIT licence](LICENSE). You may use, copy,
modify, and redistribute it, including commercially, provided the copyright notice
and licence text travel with it.

Where this document and the MIT licence disagree, the licence wins.

## No warranty

Stated in the licence and repeated here because it matters more for a keyboard
than for most software:

> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED.

This is a beta. It has bugs we know about, listed in the README, and bugs we do
not. An input method sits between you and everything you type, so a defect can
mean a mistyped message, a lost draft, or text going somewhere you did not
intend. We test on real hardware and we write down what we could not verify, but
we cannot promise correctness.

Do not rely on it for anything where a typing error would be expensive, and do
not treat the clipboard history as a backup of anything.

## No liability

To the extent the law allows, the authors are not liable for any loss arising from
using this software. That includes lost or corrupted text, lost stickers, and
anything that follows from a bug.

The parts of consumer law where you live that cannot be excluded by agreement are
not excluded by this. If your jurisdiction gives you rights this paragraph appears
to remove, you keep them.

## What is yours

Everything you make with it. Stickers, themes, layouts, your dictionary. No
licence to any of it is claimed, and none of it is transmitted anywhere, so no
such licence would be any use.

## What you are responsible for

- What you type, send, and share, and any consequences of it.
- Having the right to use images you import as stickers. The app cannot tell
  whether a picture is yours.
- Anything you build on a fork. If you distribute a modified version, the
  modifications are yours, and it needs to be clear it is not this build.

## Age

Not directed at children under 13, and there is nothing to register for. Anyone
can install it; nothing is collected from anyone.

## No support commitment

Two people work on this in their own time. Issues get read and most get answered,
but there is no response-time promise and no obligation to fix any particular bug
or accept any particular patch.

Security reports are the exception in practice: [SECURITY.md](SECURITY.md) sets
out how to send one privately and what to expect.

## Beta builds specifically

While the version string says BETA or ALPHA, expect:

- Bugs that reach shipped builds. The one in `v0.1.5-BETA` before this release
  made every setting appear to do nothing until the app restarted.
- Changes that are not backwards compatible with saved themes and layouts,
  although we try hard to avoid it and there are tests pinning that promise.
- Release APKs signed with a different key than debug builds, so switching between
  them requires an uninstall and loses local data.

The app tells you it is a beta on its own settings screen, derived from the
version string rather than a separate flag, so a stable build cannot mislabel
itself.

## If these terms change

Changes get committed to this file with the date above updated, and noted in
[CHANGELOG.md](CHANGELOG.md) if they are material. There is no mechanism for
retroactive terms here: the version you have is governed by the file that shipped
with it, and the git history shows what that said.

## Contact

The [issue tracker](https://github.com/uncoalesced/fluxboard/issues).
