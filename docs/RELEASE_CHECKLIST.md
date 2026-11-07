# Release checklist (Voxelium 0.3.1)

Status as of 2026-11-07.
- ✅ Accent colour picker (`23f3c20`) and the new app icon (`3ebd1ab`) on `main`. Version 0.3.1, code 301, the release notes and the release tools in the release-prep commits. Release notes: [RELEASE_NOTES_0.3.1.md](RELEASE_NOTES_0.3.1.md).
- ✅ `./gradlew :VoxeliumCore:test`: 134 tests pass (1 skipped: the network tests, which run only with `-Pvoxelium.networkTests=true`). The icon changes only image resources.
- ⏳ Release APKs from a clean tree with `tools/build_release.sh 0.3.1`, signed with `tools/sign_release.sh 0.3.1` into `~/Desktop/Voxelium-0.3.1/`: byte size and full SHA-256 of each to be filled in.
- ⏳ Checks on the APKs: versionName 0.3.1, versionCode 301, minSdk 26; notices identical; no keys, keystores, pairing files or tokens inside; the CurseForge key only from the git-ignored file; the same release key as 0.3.0; both `.sha256` pass.
- ⏳ Device pass on that exact arm64 APK: installed build confirmed (versionCode 301 and the installed APK's SHA-256), the new icon in the app drawer, the accent colour, and a game started.
- ⏳ Committed and tagged `v0.3.1`.
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
