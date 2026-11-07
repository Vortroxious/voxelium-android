# Voxelium 0.3.1

**A new icon, and choose your own colour.**

> Unofficial modified version of [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2); please report problems here, not to the Zalith Launcher team. Not affiliated with Mojang or Microsoft. Voxelium includes no Minecraft game files; you need to own Minecraft: Java Edition to play online. Android 8.0 or newer. See the [install guide](../README.md#install).

## New
- **New app icon:** grey cubes with a grass block, instead of Zalith Launcher 2's yellow cube. Android 13 and later can also show it in your theme's colours.
- **Accent colour.** Settings → Launcher → **Accent colour**: pick one of 25 colours for buttons, highlights and the side bar.
  - Voxelium green (the default), red, crimson, coral, orange, amber, gold, yellow, lime, green, mint, teal, cyan, sky blue, blue, navy, indigo, purple, violet, magenta, pink, brown, gray, white and black.
  - The launcher changes colour as soon as you tap one, and keeps it after a restart.
  - Light and dark mode each get their own shade, so text stays easy to read. White turns charcoal in light mode and black turns graphite in dark mode, so they never vanish into the background.
  - The same 25 colours as Voxelium for iOS.

## Fixed
- No fixes in this release.

## Tested
On a Galaxy Note10+ (SM-N975F):
- **The release APK** (`Voxelium-0.3.1-arm64-v8a.apk`, versionCode 301), confirmed as the installed build: the new icon in the app drawer, the update kept the accent colour, account and installed version, and Minecraft 1.21.11 started to the title screen and quit cleanly.
- With test builds of this release: the new icon in the app drawer, nothing clipped by Samsung's icon shape; the colour picker, red in dark mode and blue in light mode applied at once across the side bar, Settings and Play, kept after restarting the app; an offline account and Minecraft 1.21.11 started to the title screen.

Everything else is unchanged from [0.3.0](RELEASE_NOTES_0.3.0.md).

## Updating
Install over 0.3.0. It is signed with the same key, so Android installs it as an update: your versions, accounts, worlds and settings stay.

## Known limits
- The accent colour is for the launcher only. The on-screen game controls keep their look.
- Voxelium's own pages are in English only, whichever language is chosen.
- 32-bit phones (armeabi-v7a) can only run older Minecraft versions.
