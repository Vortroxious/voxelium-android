#!/bin/zsh
# Builds the unsigned release APKs (arm64-v8a and armeabi-v7a) from a clean, committed tree into
# build/release-<version>/, with build.info (commit, toolchain, whether the CurseForge key is built
# in) that tools/sign_release.sh puts into each APK's .info file. The CurseForge key is never printed.
#
# Usage (from the repository root): tools/build_release.sh 0.3.1
set -euo pipefail

version=${1:?usage: tools/build_release.sh <version>}
out="build/release-$version"

# The release must come from exactly what is committed.
if [[ -n $(git status --porcelain) ]]; then
  echo "The working tree isn't clean. Commit or stash first:"; git status --short; exit 1
fi
props=ZalithLauncher/gradle.properties
name=$(sed -n 's/^launcher_version_name=//p' $props)
code=$(sed -n 's/^launcher_version_code=//p' $props)
[[ $name == "$version" ]] || { echo "$props says $name, not $version."; exit 1; }
commit=$(git rev-parse HEAD)

mkdir -p "$out"
rm -f "$out"/Voxelium-"$version"-*.apk(N)
for arch abi in arm64 arm64-v8a arm armeabi-v7a; do
  echo "==> assembleRelease -Darch=$arch"
  ./gradlew :ZalithLauncher:assembleRelease -Darch=$arch -q
  cp "ZalithLauncher/build/outputs/apk/release/Voxelium-$version-$abi.apk" "$out/"
done

# Nothing secret may ship inside: signing keys, keystores, pairing files, tokens, the key file.
for apk in "$out"/Voxelium-"$version"-*.apk; do
  if unzip -l "$apk" | grep -iE '\.(jks|keystore|p12|pem|key|mobiledevicepairing)$|pairing|token|curseforge' ; then
    echo "Found a file that must not ship in ${apk:t}."; exit 1
  fi
done

# The CurseForge key comes from CURSEFORGE_API_KEY or the git-ignored Config/curseforge.key
# (getKeyFromLocal in ZalithLauncher/build.gradle.kts). Record only whether there was one.
if [[ -n ${CURSEFORGE_API_KEY:-} ]]; then cf="built in (CURSEFORGE_API_KEY)"
elif [[ -s Config/curseforge.key ]]; then cf="built in (Config/curseforge.key, git-ignored)"
else cf="none (CurseForge browsing off)"; fi

gradle_v=$(./gradlew -v 2>/dev/null)
{
  echo "commit=$commit"
  echo "version=$name ($code)"
  echo "curseforge_key=$cf"
  echo "agp=$(sed -n 's/^agp = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
  echo "kotlin=$(sed -n 's/^kotlin = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
  echo "gradle=$(echo "$gradle_v" | sed -n 's/^Gradle //p')"
  echo "jdk=$(echo "$gradle_v" | sed -n 's/^Launcher JVM: *//p')"
  echo "ndk=$(sed -n 's/.*ndkVersion = "\(.*\)".*/\1/p' ZalithLauncher/build.gradle.kts)"
  echo "built=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$out/build.info"

echo
cat "$out/build.info"
ls -la "$out"/*.apk
echo "Next: tools/sign_release.sh $version"
