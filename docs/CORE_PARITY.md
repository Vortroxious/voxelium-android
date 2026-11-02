# VoxeliumCore parity (iOS Swift ↔ Android Kotlin)

The Android launcher's logic lives in the `:VoxeliumCore` module (`VoxeliumCore/src/main/kotlin/io/github/voxelium/core`), a Kotlin port of the iOS app's Swift `Sources/VoxeliumCore` (repository `Vortroxious/Voxelium-launcher`).

ZalithLauncher2 code remains only as the engine: Java launch over JNI, renderers, LWJGL/SDL, and touch controls.

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
| Paths/AppPaths.swift | paths/AppPaths.kt | ✅ | e28b28b | `gameDir` can be passed in (Android uses its existing `.minecraft`); no `controlsDir` |
| Paths/AppPathsMigration.swift | | ➖ | | iOS 0.1.0 data move only |
| Manifest/VersionManifest.swift | manifest/VersionManifest.kt, manifest/MojangJson.kt | ✅ | e28b28b | |
| Manifest/VersionInfo.swift | manifest/VersionInfo.kt | ✅ | e28b28b | URLs kept as strings |
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
| Content/Modpack.swift | content/Modpack.kt (+ modpack search in content/Modrinth.kt) | ✅ partly | e28b28b | index, checks, record, search; `ModpackInstaller.install` (needs GameInstaller) comes with phase 6 |
| Content/WorldManager.swift | content/WorldManager.kt | ✅ | e28b28b | ZIP64 works (java.util.zip); entries streamed to disk; not yet used by the app (Voxelium Worlds screen, phase 7) |
| Launch/GameInstaller.swift | | ⏳ 6 | | |
| Launch/ModLoaders.swift | launch/ModLoader.kt (enum only) | ⏳ 6 | e28b28b | `ModLoader` ported in phase 4; installers and `isAvailable` in phase 6 |
| Launch/ForgeInstaller.swift | | ⏳ 6 | | |
| Launch/ForgeLaunch.swift | | ⏳ 6 | | |
| Launch/VersionResolver.swift | | ⏳ 6 | | |
| Launch/GameFilePlanner.swift | | ⏳ 6 | | |
| Launch/GameRuntime.swift | | ⏳ 6 | | |
| Launch/LaunchPlanner.swift | launch/LaunchIdentity.kt (LaunchIdentity only) | ⏳ 6 | e28b28b | `LaunchIdentity` ported in phase 2 for accounts |
| Launch/VersionDeleter.swift | | ⏳ 6 | | |
| Launch/GameRenderer.swift | | ⏳ 6 | | maps onto ZalithLauncher2's renderers |
| Content/GameOptions.swift | | ⏳ 6 | | |
| Content/IrisSettings.swift | | ⏳ 6 | | |
| Runtime/RuntimeCatalog.swift | | ➖ | | ZalithLauncher2's `RuntimesManager` (bundled JREs, `.so`) is kept |
| Runtime/MachO.swift | | ➖ | | iOS only |
| Controls/ControlLayout.swift | | ➖ | | ZalithLauncher2's controls and editor are kept |
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
| ContentManagerTests (ContentManagerTests suite) | content/ContentManagerTests.kt | 7 (Iris, GameOptions and ShaderLaunchAdvice suites come with phase 6) |
| ModrinthTests | content/ModrinthTests.kt | 5 |
| CurseForgeTests | content/CurseForgeTests.kt | 7 |
| WorldManagerTests | content/WorldManagerTests.kt | 5 |
| ModpackTests | content/ModpackTests.kt | 3 (+1 Android: overrides planning); `installsAVanillaPackWithOverrides` with phase 6 |
| — | download/NetworkTests.kt | 1, opt-in, real network |

Swift's parameterised cases (`mavenPaths`, `rejectsTraversal`) are loops inside a single Kotlin test.
