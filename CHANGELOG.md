# Changelog

## 0.3.1
- **Accent colour:** Settings → Launcher → Accent colour. 25 colours (the same as Voxelium for iOS), each with a light-mode and a dark-mode shade. The launcher recolours at once and keeps the choice. Release notes: [docs/RELEASE_NOTES_0.3.1.md](docs/RELEASE_NOTES_0.3.1.md).

## 0.3.0 (2026-11-06)

Voxelium for Android now has its own interface, and its touch controls are the same as Voxelium for iOS.

### New interface
- **Play** is the home screen: the last played version with a big Play button, every installed version beside it, and who you play as.
- **Install**: Minecraft with Vanilla, Fabric, Quilt, Forge or NeoForge, and Modpacks (Modrinth search and `.mrpack` import).
- **A page for each version** with tabs for mods, resource packs, shader packs and worlds, and its own settings: memory, Java, renderer and its options, Vulkan driver, graphics API, Java and game arguments, joining a server on start, the Vulkan check, rename, verify and repair, and delete.
- **Accounts**: Microsoft sign-in, offline accounts, other sign-in servers, skins and capes, all on one page.
- **Settings** in seven sections: Game, Graphics, Java (including importing a runtime), Gamepad (with button mapping and profiles), Motion (aim by moving the phone), Launcher (appearance, language, full screen, animations) and About (version, update check, links, and Credits and licences).
- A new first-start screen, crash page (with the end of the game log), and log viewer.

### Touch controls like iOS
- The same default layout, gestures, drawer and runtime menu as Voxelium for iOS, from the same `layout.json` format. A layout made on iOS can be imported unchanged.
- **Controls** page: drag buttons on a preview of the game screen; edit keys, mouse buttons, specials, size, toggle and where a button shows; change the joystick size and the opacity; reset to the default.

### Removed
- Zalith Launcher 2's screens: its home screen and cards, the Download tab, the settings and version settings screens (including Modify Version, version icons and descriptions, and the custom game folder), its control layouts and editor, version export, the screenshot and server-list pages, and the background and colour themes.
- Terracotta multiplayer and the in-app file manager. Voxelium's files can still be browsed in Android's Files app.

### Notes
- Settings, accounts and installed versions from 0.2.0 carry over.
- Layouts made in Zalith Launcher 2's control editor aren't used any more. The controls start from the iOS default layout.
- Third-party licences are listed in `THIRD_PARTY_NOTICES.md` and in the app under Settings → About → Credits and licences.

## 0.2.0 (2026-11-05)
- The Versions screen is the home screen, with installs, mods and packs, and worlds running on VoxeliumCore, the core shared with Voxelium for iOS.
- Microsoft sign-in tokens are encrypted with the Android Keystore.
- CurseForge files whose authors allow only their website are linked there, never downloaded another way.
- Update checks use this repository's GitHub releases.

## 0.1.0 (2026-11-01)
- First version: a fork of Zalith Launcher 2 2.7.2, renamed Voxelium for Android, with its own application ID.
