# Voxelium for Android

An Android launcher for **Minecraft: Java Edition**, the Android sibling of [Voxelium for iOS](https://github.com/Vortroxious/Voxelium-launcher).

> **Unofficial modified version of [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2).** Voxelium is built on Zalith Launcher 2 (GPL-3.0). Please report problems here, not to the Zalith Launcher team. See [NOTICE.md](NOTICE.md).

Not affiliated with or endorsed by Mojang Studios, Microsoft or the ZalithLauncher team. Voxelium does not include any Minecraft game files. They are downloaded from Mojang's servers to your device, and you need to own Minecraft: Java Edition to play online.

## Status
Early development (0.1.x). Target: parity with Voxelium for iOS 2.4:

- Microsoft sign-in, and offline play with the saved sign-in
- Version list; vanilla, Fabric, Quilt, Forge and NeoForge installs
- Java 8 / 17 / 21 / 25 runtimes
- Renderers: MobileGlues, gl4es, Zink + Turnip (Adreno), ANGLE
- On-screen touch controls with a layout editor
- Mods, resource packs, shader packs; Modrinth and CurseForge browsing; Modrinth modpacks
- World import/export, and update checks against this repo's GitHub releases

## Device requirements
- **Android 8.0 or newer.** Builds exist for arm64-v8a, armeabi-v7a, x86 and x86_64.
- **Recommended:** a 64-bit phone with 4 GB of RAM or more (6–8 GB for 1.21+ / 26.x or mods), and a recent Adreno or Mali GPU.
- 32-bit or low-memory phones can only run older Minecraft versions. Turnip/Zink needs an Adreno GPU.

## Install
Download the APK from [Releases](https://github.com/Vortroxious/Voxelium-android/releases) and open it on your phone. You'll need to allow installs from that source.

## Build
Requirements: Android Studio (its bundled JDK works), Android SDK platform 37, and **NDK 25.2.9519653**.

```bash
./gradlew :ZalithLauncher:assembleDebug -Darch=arm64
```

`-Darch` can be `arm64`, `arm`, `x86` or `x86_64`. Leave it out to build an APK for every ABI. The APK is written to `ZalithLauncher/build/outputs/apk/debug/`.

Optional local files (all git-ignored, never commit them):
- `Config/curseforge.key`: CurseForge API key. Without it, CurseForge browsing is off.
- `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`): release signing. Debug builds use Android's local debug key.

## License
GPL-3.0, with Zalith Launcher 2's additional terms under GPLv3 §7. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
