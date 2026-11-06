# Notices

This project is licensed under GPL-3.0 (see `LICENSE`).

## Fork of Zalith Launcher 2
Voxelium for Android is an **unofficial modified version** of [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2) (GPL-3.0, Copyright (C) 2025 MovTery and contributors). It is not affiliated with, or endorsed by, the ZalithLauncher team.

- Imported from upstream tag `2.7.2`, commit `da53e5f06d2a205d44ec28d050f97864b85ca0a8` (2026-10-20), as a snapshot without upstream git history. Upstream's signing keystores (`zalith_launcher.jks`, `zalith_launcher_debug.jks`) and default keystore passwords were **not** imported.
- Upstream source files keep their original copyright headers. Voxelium changes are marked with `Voxelium:` comments.
- Upstream READMEs, including their third-party library table, are kept in [`docs/upstream/`](docs/upstream/).

### What is Voxelium's and what remains Zalith Launcher 2's
- **Voxelium's own:** the interface (`ui/voxelium/`: theme, Play, Install, version pages, Accounts, Controls, Settings, first start, crash and log pages), the in-game touch controls and their editor, and VoxeliumCore (`VoxeliumCore/`), the launcher logic shared with Voxelium for iOS.
- **Kept from Zalith Launcher 2:** the engine (Java launch over JNI, renderers, LWJGL/SDL, Java runtime management, gamepad and gyroscope input), the settings and account storage, the Microsoft sign-in flow and its account dialogs, and its licence text screen. Everything Zalith Launcher 2's About screen credited (its author, acknowledgements, libraries and licences, loaded plugins) is shown on Voxelium's Settings → About → Credits and licences.
- **Removed:** Zalith Launcher 2's settings and version settings screens (including Modify Version, version icons and descriptions, the custom game folder and touch vibration), the Download tab and its search, details and favourites screens (their search and download code stays as engine), Terracotta multiplayer (and its `Terracotta` module), the in-app file manager, Zalith Launcher 2's home screen and cards, its control layouts and control editor, version export, and the screenshot and server-list pages. The background image and colour themes are no longer applied.

### Zalith Launcher 2's additional terms (GPLv3 §7), and how Voxelium meets them
1. *A modified version must change the name or version, must not use "ZalithLauncher" or "ZL" or a confusingly similar name, and must say "Unofficial Modified Version" on its startup screen or main interface.* This app is named **Voxelium**, with its own version (0.x) and application ID (`io.github.voxelium.launcher`). The Play screen and Settings → About show a permanent notice that it is an Unofficial Modified Version of Zalith Launcher 2.
2. *The copyright notices the program displays must not be removed.* Zalith Launcher 2's copyright notices, acknowledgements and licence texts are kept on Voxelium's Settings → About → Credits and licences.

## Lineage
Zalith Launcher 2 uses [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) (LGPL-3.0/GPL-3.0) as its core launching engine. The native launch code under `ZalithLauncher/src/main/jni` comes from PojavLauncher.

## Bundled third-party components
The APK bundles prebuilt components from upstream, under their own licences. These include:
- OpenJDK runtimes 8/17/21/25 (GPL-2.0 with Classpath Exception)
- LWJGL (BSD-3-Clause)
- MobileGlues (LGPL-2.1)
- gl4es / NG-GL4ES (MIT)
- Mesa (Zink, Turnip; MIT)
- ANGLE (BSD-3-Clause)
- SDL3 / sdl2-compat (Zlib)
- OpenAL Soft (LGPL-2.0)
- ByteHook (MIT)
- MMKV (BSD-3-Clause)
- AndroidX/Jetpack libraries (Apache-2.0)

The full list with links is in [`docs/upstream/README_EN_US.md`](docs/upstream/README_EN_US.md#open-source-libraries-and-licenses). A complete THIRD_PARTY_NOTICES file will be generated before the first release.

## Services and trademarks
- "Minecraft" is a trademark of Mojang Synergies AB. Voxelium does not include or redistribute any Minecraft game files. They are downloaded from Mojang's servers to the user's device.
- Microsoft sign-in uses the public OAuth client ID `00000000402b5328`, the same one Amethyst and PojavLauncher use.
- The CurseForge API key is supplied at build time from the git-ignored `Config/curseforge.key` and is never committed. CurseForge files without a `downloadUrl` are never downloaded another way. The version list links to the project's files page on CurseForge's website, and a modpack that needs such files stops with a list of their CurseForge links.
- Modrinth content is fetched from the public Modrinth API.
