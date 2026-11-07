# Release checklist (Voxelium 0.3.1)

Status as of 2026-11-07.
- ✅ Accent colour picker (`23f3c20`) and the new app icon (`3ebd1ab`) on `main`. Version 0.3.1, code 301, the release notes and the release tools in the release-prep commits. Release notes: [RELEASE_NOTES_0.3.1.md](RELEASE_NOTES_0.3.1.md).
- ✅ `./gradlew :VoxeliumCore:test`: 134 tests pass (1 skipped: the network tests, which run only with `-Pvoxelium.networkTests=true`). The icon changes only image resources.
- ✅ Release APKs from a clean tree at `f8e28cd` (`tools/build_release.sh 0.3.1`, built 2026-11-07 02:39Z), signed with the release key by `tools/sign_release.sh 0.3.1` into `~/Desktop/Voxelium-0.3.1/`:
  - `Voxelium-0.3.1-arm64-v8a.apk`: 173,505,325 bytes, SHA-256 `dbf8c1efc86de6927998d6aac8a1dcf5abbeb4a6d47608b3031b7da0371e9b8d`.
  - `Voxelium-0.3.1-armeabi-v7a.apk`: 166,083,526 bytes, SHA-256 `e957205ad2d8057f3832a3c096aff191cec526b2005050c69209a31eed06c55f`.
- ✅ Checks on the APKs:
  - versionName 0.3.1, versionCode 301, package `io.github.voxelium.launcher`, minSdk 26; one ABI each.
  - The new icon inside (`aapt2 dump resources`: `ic_launcher_foreground` and `ic_launcher_monochrome` as PNGs, background `#ffffffff`).
  - `THIRD_PARTY_NOTICES.md` in the release folder identical to the repository's (`cmp`).
  - No signing keys, keystores, pairing files or tokens inside; no `.idsig` left in the folder.
  - The CurseForge key built in from the git-ignored `Config/curseforge.key`; in no tracked file.
  - Signed with the same release key as 0.3.0 (certificate SHA-256 `b9c9407042fcf084eb6c9d5df39e9c7bf012073eebe6bef0b022cceadb2e1178`), so it installs as an update.
  - Both `.sha256` files pass `shasum -a 256 -c`.
- ✅ Device pass on that exact arm64 APK, Galaxy Note10+ (SM-N975F), 2026-11-07 09:45 local:
  - Installed over the earlier 0.3.1 build; confirmed with `adb shell dumpsys package io.github.voxelium.launcher` (versionName 0.3.1, versionCode 301). The installed APK's SHA-256 is `dbf8c1ef…9b8d`, the release file.
  - The new icon in the app drawer, in Samsung's icon shape, nothing clipped.
  - The update kept the blue accent, the offline account and Minecraft 1.21.11.
  - 1.21.11 started to the title screen ("Minecraft 1.21.11/Voxelium0.3.1", 1.5 of 7.3 GB) and quit back to the launcher.
- ✅ Committed and tagged `v0.3.1`.
- 👤 Push `main` and `v0.3.1`. Create the GitHub Release **v0.3.1** with both APKs, their `.sha256` files and `THIRD_PARTY_NOTICES.md` from `~/Desktop/Voxelium-0.3.1/`, and paste [RELEASE_NOTES_0.3.1.md](RELEASE_NOTES_0.3.1.md).

Earlier build of 0.3.1, replaced before publishing: built from `207219c` without the new icon, signed (arm64 SHA-256 `dc7afd9e…647d`, armeabi-v7a `a1944ad6…79a7`) and passed on the Note10+ (first start, blue accent in light mode, an offline account, Minecraft 1.21.11 to the title screen and back). The user then asked for the new icon in 0.3.1; the tag `v0.3.1` had not been pushed, so it was moved to the new build.

# Release checklist (Voxelium 0.3.0)

Recorded afterwards (2026-11-07), when the iOS release layout was adopted.
- ✅ Voxelium's own interface and the iOS touch controls (U0–U5, the leftovers), release prep `0224cc2`, tagged `v0.3.0`. Release notes: [RELEASE_NOTES_0.3.0.md](RELEASE_NOTES_0.3.0.md).
- ✅ Release APKs built from `0224cc2` and signed with the release key (certificate SHA-256 `b9c9407042fcf084eb6c9d5df39e9c7bf012073eebe6bef0b022cceadb2e1178`):
  - `Voxelium-0.3.0-arm64-v8a.apk`: 173,341,036 bytes, SHA-256 `64d1b4f5e0b726d0b7908b293d6d408589b93368b99c42649082e8781c1f31af`.
  - `Voxelium-0.3.0-armeabi-v7a.apk`: 165,919,237 bytes, SHA-256 `718505a8c1ded3724a0f65ad3766b7c8ea33aadf9af12596f915a8a55c007b44`.
  - The files on the GitHub release have the same sizes and SHA-256 (GitHub's asset digests).
- ✅ No signing keys, keystores, pairing files or tokens in either APK.
- ⚠️ Device pass: done on the same code (the minified arm64 release build) signed with the debug key, not on the signed APK. The release app on the test phone is that debug-key copy (SHA-256 `e0ce7f30…37e4`, not the published file).
- ✅ Published on GitHub on 2026-11-06.
