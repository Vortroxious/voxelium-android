**Unofficial modified version of [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2).** Please report problems here, not to the Zalith Launcher team. Not affiliated with Mojang or Microsoft. Voxelium includes no Minecraft game files; you need to own Minecraft: Java Edition to play online.

## Voxelium 0.3.0

Voxelium for Android now has its own interface, and its touch controls are the same as Voxelium for iOS.

### New interface
- **Play** is the home screen: the last played version with a big Play button, every installed version beside it, and who you play as.
- **Install**: Minecraft with Vanilla, Fabric, Quilt, Forge or NeoForge, and Modpacks (Modrinth search and `.mrpack` import).
- **A page for each version**: mods, resource packs, shader packs and worlds, plus its own settings (memory, Java, renderer and its options, Vulkan driver, graphics API, arguments, joining a server on start, the Vulkan check), rename, verify and repair, and delete.
- **Accounts**: Microsoft sign-in, offline accounts, other sign-in servers, skins and capes on one page.
- **Settings**: Game, Graphics, Java (import your own runtime), Gamepad (button mapping and profiles), Motion (aim by moving the phone), Launcher (appearance, language, full screen, animations) and About (update check, credits and licences).
- New first-start screen, crash page (with the end of the game log) and log viewer.

### Touch controls like iOS
- The same layout, gestures, drawer and runtime menu as Voxelium for iOS, in the same `layout.json` format. A layout made on iOS can be imported unchanged.
- **Controls** page: drag buttons on a preview of the game screen; edit keys, mouse buttons, size, toggle and where each button shows; change the joystick size and opacity; reset to the default.

### Removed
- Zalith Launcher 2's own screens: its home screen, the Download tab, its settings and version settings (including Modify Version, version icons and the custom game folder), its control layouts and editor, version export, screenshots, the server list, and background and colour themes.
- Terracotta multiplayer and the in-app file manager. Voxelium's files can still be browsed in Android's Files app.

### Good to know
- Settings, accounts and installed versions from 0.2.0 carry over.
- Layouts from Zalith Launcher 2's control editor aren't used; the controls start from the iOS default layout.

## Which file?
- **Voxelium-0.3.0-arm64-v8a.apk**: almost every phone from the last several years. Pick this one if unsure.
- **Voxelium-0.3.0-armeabi-v7a.apk**: older 32-bit phones only. These can run older Minecraft versions.

Android 8.0 or newer. 4 GB of RAM or more recommended (6–8 GB for 1.21+, 26.x or mods). Allow installs from your browser or file manager when Android asks. The first start unpacks the bundled Java runtimes, which takes about a minute.

Licences: [THIRD_PARTY_NOTICES.md](https://github.com/Vortroxious/Voxelium-android/blob/v0.3.0/THIRD_PARTY_NOTICES.md), and in the app under Settings → About → Credits and licences. GPL-3.0 with Zalith Launcher 2's additional terms; see [NOTICE.md](https://github.com/Vortroxious/Voxelium-android/blob/v0.3.0/NOTICE.md).

### SHA-256
```
64d1b4f5e0b726d0b7908b293d6d408589b93368b99c42649082e8781c1f31af  Voxelium-0.3.0-arm64-v8a.apk
718505a8c1ded3724a0f65ad3766b7c8ea33aadf9af12596f915a8a55c007b44  Voxelium-0.3.0-armeabi-v7a.apk
```
