# VoxeliumCore parity (iOS Swift ↔ Android Kotlin)

The Android launcher's logic lives in the `:VoxeliumCore` module (`VoxeliumCore/src/main/kotlin/io/github/voxelium/core`), a Kotlin port of the iOS app's Swift `Sources/VoxeliumCore` (repository `Vortroxious/Voxelium-launcher`).

ZalithLauncher2 code remains only as the engine: Java launch over JNI, renderers, LWJGL/SDL, Java runtimes, gamepad and gyroscope input. The touch controls are VoxeliumCore's `ControlLayout` with the iOS gestures (see the Controls row).

**Rules:**
- Kotlin files mirror the Swift file names. Each header names the Swift file and iOS commit it was ported from.
- Tests are ported with the same names and the **same fixture files** (`VoxeliumCore/src/test/resources/fixtures`, copied from `Tests/VoxeliumCoreTests/Fixtures`).
- When the iOS core changes, port the change and its test, then update the "iOS commit" column.
- Run the tests with `./gradlew :VoxeliumCore:test`. Add `-Pvoxelium.networkTests=true` to include real downloads.

**Status values:**
- ✅ ported
- ⏳ planned (phase number from the plan)
- ➖ not ported (iOS-only, or ZalithLauncher2's version is kept)

| Swift (`Sources/VoxeliumCore/…`) | Kotlin (`io.github.voxelium.core…`) | Status | iOS commit | Notes |
|---|---|---|---|---|
| Paths/AppPaths.swift | paths/AppPaths.kt | ✅ | e28b28b | `gameDir` can be passed in (Android uses its existing `.minecraft`); `controlsDir` is `root/controls` as on iOS |
| Paths/AppPathsMigration.swift | | ➖ | | iOS 0.1.0 data move only |
| Manifest/VersionManifest.swift | manifest/VersionManifest.kt, manifest/MojangJson.kt | ✅ | e28b28b | |
| Manifest/VersionInfo.swift | manifest/VersionInfo.kt | ✅ | e28b28b | URLs kept as strings; Android addition: `Downloads.clientMappings` (for DOWNLOAD_MOJMAPS) |
| Manifest/Rules.swift | manifest/Rules.kt | ✅ | e28b28b | `RulePlatform.iOSDefault` → `RulePlatform.DEFAULT` (linux/aarch64) |
| Manifest/AssetIndex.swift | manifest/AssetIndex.kt | ✅ | e28b28b | |
| Download/FileHash.swift | download/FileHash.kt | ✅ | e28b28b | `MessageDigest` instead of CryptoKit |
| Download/DownloadManager.swift | download/DownloadManager.kt, download/OkHttpTransport.kt | ✅ | e28b28b | OkHttp instead of URLSession |
| Logging/Log.swift | logging/Logger.kt | ✅ | e28b28b | `sink` instead of os_log; the app routes it to android.util.Log |
| Accounts/MicrosoftAuth.swift (+ `HTTPHeaders`/`get` from ModLoaders.swift) | accounts/MicrosoftAuth.kt, accounts/HttpClient.kt | ✅ | e28b28b | adds an optional `onStep` progress callback; the app adapter is `game/account/microsoft/VoxeliumMicrosoftAuth.kt` |
| Accounts/OfflineIdentity.swift | accounts/OfflineIdentity.kt | ✅ | e28b28b | ZalithLauncher2 still creates offline accounts; the core gives the same UUIDs |
| Accounts/Keychain.swift | (app) game/account/AccountSecrets.kt | ✅ | e28b28b | Android Keystore AES-GCM; tokens and third-party passwords are encrypted in the Room `accounts` table |
| Launch/UpdateCheck.swift | launch/UpdateCheck.kt | ✅ | e28b28b | repository is a parameter; releases also carry body, date and assets (the app offers the APK); app adapter `upgrade/GithubReleaseSource.kt` |
| Config/SettingsStore.swift | config/SettingsStore.kt | ✅ | e28b28b | `effectiveRenderScale` not ported (iOS window code); not used by the app until the Voxelium UI (phase 7) |
| Content/Modrinth.swift | content/Modrinth.kt | ✅ | e28b28b | not yet used by the app: connected with the Voxelium content screens (phase 7) |
| Content/CurseForge.swift | content/CurseForge.kt | ✅ | e28b28b | as Modrinth; ZalithLauncher2's browser already follows the website-only rule meanwhile |
| Content/ContentManager.swift | content/ContentManager.kt | ✅ | e28b28b | as Modrinth |
| Content/ZipReader.swift | content/ZipReader.kt | ✅ | e28b28b | `java.util.zip` instead of the hand-written reader; `Closeable` |
| Content/Modpack.swift | content/Modpack.kt (+ modpack search in content/Modrinth.kt) | ✅ | e28b28b | |
| Content/WorldManager.swift | content/WorldManager.kt | ✅ | e28b28b | ZIP64 works (java.util.zip); entries streamed to disk; not yet used by the app (Voxelium Worlds screen, phase 7) |
| Launch/GameInstaller.swift | launch/GameInstaller.kt | ✅ | e28b28b | |
| Launch/ModLoaders.swift | launch/ModLoader.kt | ✅ | e28b28b | ModLoader, LoaderVersion, ModLoaderMeta, ModLoaderInstaller; Android fix: builds sorted newest first (`compareVersions`), see below |
| Launch/ForgeInstaller.swift | launch/ForgeMeta.kt, launch/ForgeInstaller.kt | ✅ | e28b28b | `relocatePlan` not ported (iOS data folder moves on reinstall; Android's doesn't). Android runs the processor plan at install time (decided 2026-11-02): plan reader launch/ProcessorPlan.kt (from JavaRuntime/voxelium-boot Boot.java), runner in the app (`game/voxelium/ForgeProcessorRunner.kt`) |
| Launch/ForgeLaunch.swift | launch/ForgeLaunch.kt | ✅ | e28b28b | iOS boot-jar names not ported |
| Launch/VersionResolver.swift | launch/VersionResolver.kt | ✅ | e28b28b | |
| Launch/GameFilePlanner.swift | launch/GameFilePlanner.kt | ✅ | e28b28b | |
| Launch/GameRuntime.swift | launch/GameRuntime.kt | ✅ | e28b28b | the Android app bundles LWJGL 3.3.3 and 3.4.1 |
| Launch/LaunchPlanner.swift | launch/LaunchPlanner.kt, launch/LaunchIdentity.kt | ✅ | e28b28b | launcher name/version are parameters |
| Launch/VersionDeleter.swift | launch/VersionDeleter.kt | ✅ | e28b28b | |
| Launch/GameRenderer.swift | launch/GameRuntime.kt (RendererChoice only) | ✅ partly | e28b28b | `GameRenderer` (MobileGlues/ANGLE, KosmicKrisp) is iOS-only; Android keeps ZalithLauncher2's renderers |
| Content/GameOptions.swift | content/GameOptions.kt | ✅ | e28b28b | GameOptions, ShaderLaunchAdvice |
| Content/IrisSettings.swift | content/IrisSettings.kt | ✅ | e28b28b | |
| Runtime/RuntimeCatalog.swift | | ➖ | | ZalithLauncher2's `RuntimesManager` (bundled JREs, `.so`) is kept |
| Runtime/MachO.swift | | ➖ | | iOS only |
| Controls/ControlLayout.swift | controls/ControlLayout.kt | ✅ | e28b28b | sizes are dp where iOS uses points. `layout.json` is written in the same bytes as Swift's `JSONEncoder` (`.prettyPrinted, .sortedKeys`: upper-case UUIDs, `\/`, Swift number form) by `SwiftJson`, so a file moves between the apps unchanged. `ControlSpecial.KEYBOARD.title` says "Show keyboard" (iOS: "Show iOS keyboard"). Used by the in-game overlay `ui/voxelium/game/TouchControls.kt` (from `App/Voxelium/Native/VXTouchControls.m`, d602cd1) |
| Launch/JITReadiness.swift | | ➖ | | iOS only (Android needs no JIT workaround) |
| Launch/NetworkRoute.swift | | ➖ | | iOS only |
| Launch/StikDebugJIT.swift | | ➖ | | iOS only |

## Tests ported

| Swift test | Kotlin test | Count |
|---|---|---|
| ManifestTests, RuleTests | manifest/ManifestTests.kt (ManifestTests, RuleTests) | 9 + 5 |
| DownloadTests | download/DownloadTests.kt | 12 |
| SystemTests: PathTests, LoggerTests | paths/PathTests.kt (PathTests, LoggerTests) | 3 + 1 (+1 Android: custom `gameDir`) |
| MicrosoftAuthTests (+ PlannerTests' offline UUID) | accounts/MicrosoftAuthTests.kt | 6 + 1 |
| UpdateCheckTests | launch/UpdateCheckTests.kt | 5 |
| SystemTests: SettingsTests | config/SettingsTests.kt | 3 (not `largeInterfaceSnapsToMinecraftsGUISteps`) |
| ContentManagerTests (ContentManagerTests suite) | content/ContentManagerTests.kt | 7 |
| ModrinthTests | content/ModrinthTests.kt | 5 |
| CurseForgeTests | content/CurseForgeTests.kt | 7 |
| WorldManagerTests | content/WorldManagerTests.kt | 5 |
| ModpackTests | content/ModpackTests.kt | 4 (+1 Android: overrides planning) |
| — | download/NetworkTests.kt | 1, opt-in, real network |

Swift's parameterised cases (`mavenPaths`, `rejectsTraversal`) are loops inside a single Kotlin test.
| PlannerTests, IdentityTests | launch/PlannerTests.kt | 11 + 2 |
| GameRuntimeTests | launch/GameRuntimeTests.kt | 6 |
| InstallerTests, VersionDeleterTests | launch/InstallerTests.kt | 2 + 1 |
| ModLoaderTests | launch/ModLoaderTests.kt | 5 (+1 Android: builds sorted newest first) |
| ControlLayoutTests | controls/ControlLayoutTests.kt | 9 (+4 Android: same bytes as Swift for the default and an iOS file, unknown `special`/nulls, Swift number form; fixtures in `fixtures/controls`, written by the Swift encoder) |
| ForgeTests | launch/ModLoaderTests.kt (ForgeMetaTests), launch/ForgeTests.kt | 4 + 3 (+1 Android: installer refuses unoffered versions; +3 Android: ProcessorPlanTests); not `planFollowsTheDataFolderAfterAReinstall` (iOS-only) |
| SystemTests (GameOptions), ContentManagerTests (Iris, GraphicsBackend, ShaderLaunchAdvice) | content/GameOptionsTests.kt | 2 + 2 + 1 + 1 |

## Android changes to port back to iOS

- **Loader builds sorted newest first** (`ModLoaderMeta.versions`, `compareVersions`; found 2026-11-03). Quilt's meta server doesn't list builds in order: for 1.21.1 it starts with `0.20.0-beta.9`, then `0.24.0`, while `0.30.2` is near the end. "The first stable build" was therefore an old one. The iOS core has the same assumption. Test: `loaderBuildsAreSortedNewestFirst`.
- **Modpack name clash** (found 2026-11-03). Some packs give their files for different loaders the same name and version (Simply Optimized Reloaded's 1.21.1 Quilt and NeoForge files are both "Universalized Optimized 1.21.1 1.0"), so the second install is refused as already installed. Android handles it in the app (`VoxeliumGame.packVersionId`): the second one gets "(Loader)" appended. The same pack again is still refused. iOS has the same clash.
- **A dependency installed twice** (found 2026-11-05). The Modrinth planner only knows files it can match by SHA-1, and the CurseForge planner only knows files in `.voxelium-curseforge.json`, so a dependency installed from the other site (or from a file) was planned again: AppleSkin from CurseForge wanted Fabric API although Modrinth's Fabric API was installed. Two versions of one mod stop Fabric from starting. Android drops planned dependencies whose site slug matches the id of an installed mod (`VoxeliumContent.planModrinth`/`planCurseForge`). iOS has the same gap.

## How Android launches core installs (phase 7.2, decided 2026-11-03)

The core resolves the whole `inheritsFrom` chain (`VersionResolver`), verifies the files (`GameInstaller.install`) and picks the client jar; ZalithLauncher2's `LaunchArgs` still builds the Java arguments from the merged version, because it carries the Android pieces (its LWJGL, natives, the AWT shim, offline skins, Quick Play). `LaunchPlanner` is ported but not used by the app. Glue: `game/voxelium/CoreLaunch.kt`.
