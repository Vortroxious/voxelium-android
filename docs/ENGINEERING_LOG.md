# Engineering Log

## 2026-11-07
- **Release layout:** Voxelium for Android releases now follow Voxelium for iOS.
  - The release folder holds each APK with its `.sha256` (sha256sum format) and `.info` (key=value build facts), plus `THIRD_PARTY_NOTICES.md`.
  - Per-version notes live in `docs/RELEASE_NOTES_<version>.md`, with a section per release in `docs/RELEASE_CHECKLIST.md`.
  - `tools/build_release.sh` builds both ABIs from a clean tree and refuses to ship secret files; `tools/sign_release.sh` signs them and writes the layout.
- **0.3.0 recorded afterwards:** the published APKs match the local ones (sizes and SHA-256 equal GitHub's asset digests). Their `.sha256` and `.info` files were made from them; the toolchain lines are today's, since the build predates the tools.
- **0.3.0 device test gap:** the release app on the Note10+ is the debug-key-signed test copy (installed 2026-11-06 17:47), not the published APK. 0.3.1 gets its device pass on the signed APK itself.
- **Accent colour (`23f3c20`):** 25 colours, as on iOS.
  - `applyAccent()` replaces the accent slots of the Material scheme and the highlight colours. Voxelium green keeps the original scheme exactly.
  - Debug build on the Note10+: red applied at once and kept after a restart. Light mode wasn't checked on the phone.
- **0.3.1:** version 0.3.1 (301) for the accent colour release.
  - Built from `207219c` with `tools/build_release.sh`; the first run stopped on an empty glob in zsh, fixed with `(N)` before the build.
  - Signed by the user with the release key (same certificate as 0.3.0).
  - Device pass on the signed arm64 APK (installed SHA-256 matches): blue in light mode, offline account, 1.21.11 to the title screen and back. Details in RELEASE_CHECKLIST.
- **New app icon (`3ebd1ab`):** made from the user's cube picture with a small Pillow script (white page flood-filled to transparency from the edges, the cubes 52 dp tall in the 108 dp canvas, the monochrome mask from the faces without the grey outlines). Checked in the Note10+ app drawer.
- **0.3.1 redone with the icon:** the tag had not been pushed, so the earlier signed build (from `207219c`) was replaced; see RELEASE_CHECKLIST.
  - Rebuilt from `f8e28cd`, signed again by the user, and passed on the Note10+ as an update over the earlier build (installed SHA-256 matches; icon, accent, account and 1.21.11 kept; title screen and back).
