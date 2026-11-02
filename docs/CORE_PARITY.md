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
| Accounts/MicrosoftAuth.swift | | ⏳ 2 | | |
| Accounts/OfflineIdentity.swift | | ⏳ 2 | | |
| Accounts/Keychain.swift | | ⏳ 2 | | Android Keystore–encrypted storage |
| Launch/UpdateCheck.swift | | ⏳ 3 | | replaces `upgrade/GithubReleaseSource.kt` |
| Config/SettingsStore.swift | | ⏳ 3 | | |
| Content/Modrinth.swift | | ⏳ 4 | | |
| Content/CurseForge.swift | | ⏳ 4 | | |
| Content/ContentManager.swift | | ⏳ 4 | | |
| Content/ZipReader.swift | | ⏳ 4 | | `java.util.zip` |
| Content/Modpack.swift | | ⏳ 5 | | |
| Content/WorldManager.swift | | ⏳ 5 | | |
| Launch/GameInstaller.swift | | ⏳ 6 | | |
| Launch/ModLoaders.swift | | ⏳ 6 | | |
| Launch/ForgeInstaller.swift | | ⏳ 6 | | |
| Launch/ForgeLaunch.swift | | ⏳ 6 | | |
| Launch/VersionResolver.swift | | ⏳ 6 | | |
| Launch/GameFilePlanner.swift | | ⏳ 6 | | |
| Launch/GameRuntime.swift | | ⏳ 6 | | |
| Launch/LaunchPlanner.swift | | ⏳ 6 | | |
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
| — | download/NetworkTests.kt | 1, opt-in, real network |

Swift's parameterised cases (`mavenPaths`, `rejectsTraversal`) are loops inside a single Kotlin test.
