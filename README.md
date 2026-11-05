# Voxelium for Android

An Android launcher for **Minecraft: Java Edition**, the Android sibling of [Voxelium for iOS](https://github.com/Vortroxious/Voxelium-launcher).

> **Unofficial modified version of [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2).** Voxelium is built on Zalith Launcher 2 (GPL-3.0). Please report problems here, not to the Zalith Launcher team. See [NOTICE.md](NOTICE.md).

Not affiliated with or endorsed by Mojang Studios, Microsoft or the ZalithLauncher team. Voxelium does not include any Minecraft game files. They are downloaded from Mojang's servers to your device, and you need to own Minecraft: Java Edition to play online.

## Status
Version 0.2.0. The launcher's logic (installs, sign-in, mods, modpacks, worlds) runs on **VoxeliumCore**, the same core as Voxelium for iOS, ported to Kotlin ([docs/CORE_PARITY.md](docs/CORE_PARITY.md)). Zalith Launcher 2 provides the engine: starting Java, the renderers and the touch controls.

- **Versions** (the home screen): install Minecraft with Vanilla, Fabric, Quilt, Forge or NeoForge, with the recommended loader build preselected; play, check and repair, or delete. Forge and NeoForge finish setting up while installing.
- **Modpacks**: Modrinth search and `.mrpack` import; each pack becomes its own version.
- **Mods & packs** for each version: mods, resource packs and shader packs, switched on/off or removed; Modrinth updates; browsing Modrinth and CurseForge with required dependencies. CurseForge files whose authors only allow their website are linked there, never downloaded.
- **Worlds** for each version: import a `.zip`, share as `.zip`, delete.
- Microsoft sign-in (tokens encrypted with the Android Keystore), offline accounts, and offline play with the saved sign-in.
- Java 8 / 17 / 21 / 25 runtimes; renderers Krypton Wrapper (default), GL4ES, Zink, VirGL, Freedreno (Adreno) and Panfrost (Mali), plus Turnip on Adreno.
- On-screen touch controls with a layout editor, per-version settings (memory, renderer, Java) and logs, from Zalith Launcher 2.
- Update checks against this repo's GitHub releases.

Minecraft 26.2 and newer draw with Vulkan and need Vulkan extensions such as `VK_KHR_dynamic_rendering` and `VK_KHR_synchronization2`. The launcher's Vulkan check shows what a phone is missing; GPUs without them (the Mali-G76, for example) run up to 26.1.x.

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
