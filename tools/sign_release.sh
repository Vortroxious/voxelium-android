#!/bin/zsh
# Signs the release APKs in build/release-<version>/ (made by tools/build_release.sh) with your own
# release key and lays out ~/Desktop/Voxelium-<version>/ the way Voxelium for iOS does its releases:
# each signed APK with its <apk>.sha256 (sha256sum format) and <apk>.info (key=value build facts),
# and THIRD_PARTY_NOTICES.md. The release notes are docs/RELEASE_NOTES_<version>.md.
#
# The key lives outside the repo (default ~/.voxelium/voxelium-release.jks; set VOXELIUM_KEYSTORE to
# use another). If it doesn't exist yet, it is created first. The password is typed by you and never
# saved. Keep the keystore and its password safe: every future release must be signed with it, or
# Android won't install the update over the old version.
#
# Usage (from the repository root): tools/sign_release.sh 0.3.1
set -euo pipefail

version=${1:?usage: tools/sign_release.sh <version>}
src="build/release-$version"
out=${VOXELIUM_OUT:-$HOME/Desktop/Voxelium-$version}
keystore=${VOXELIUM_KEYSTORE:-$HOME/.voxelium/voxelium-release.jks}
alias_name=voxelium
jdk="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin"
apksigner=$(ls -d "$HOME"/Library/Android/sdk/build-tools/*/apksigner | tail -1)

apks=("$src"/Voxelium-"$version"-*.apk)
[[ -e ${apks[1]} ]] || { echo "No APKs in $src"; exit 1; }

echo "Key: $keystore"
if [[ -f $keystore ]]; then
  read -rs "VOXELIUM_KS_PASS?Keystore password: "; echo
else
  echo "No release key yet: creating one."
  read -rs "VOXELIUM_KS_PASS?New keystore password (6+ characters): "; echo
  read -rs "again?Type it again: "; echo
  [[ $VOXELIUM_KS_PASS == "$again" ]] || { echo "The passwords don't match."; exit 1; }
  unset again
  mkdir -p "${keystore:h}"; chmod 700 "${keystore:h}"
  export VOXELIUM_KS_PASS
  "$jdk/keytool" -genkeypair -keystore "$keystore" -storetype PKCS12 -alias "$alias_name" \
    -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Vortroxious, O=Voxelium" \
    -storepass:env VOXELIUM_KS_PASS
  chmod 600 "$keystore"
  echo "Created $keystore. Back it up (and the password) somewhere safe."
fi
export VOXELIUM_KS_PASS

mkdir -p "$out"
for apk in "${apks[@]}"; do
  name=${apk:t}
  "$apksigner" sign --ks "$keystore" --ks-key-alias "$alias_name" --ks-pass env:VOXELIUM_KS_PASS \
    --out "$out/$name" "$apk"
  "$apksigner" verify "$out/$name" >/dev/null
  echo "Signed $name"
done
unset VOXELIUM_KS_PASS
rm -f "$out"/*.idsig

aapt2=$(ls -d "$HOME"/Library/Android/sdk/build-tools/*/aapt2 | tail -1)
cert=$("$apksigner" verify --print-certs "$out/${apks[1]:t}" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
for apk in "${apks[@]}"; do
  name=${apk:t}
  signed="$out/$name"
  sha=$(shasum -a 256 "$signed" | cut -d' ' -f1)
  echo "$sha  $name" > "$signed.sha256"
  badging=$("$aapt2" dump badging "$signed")
  {
    echo "apk=$signed"
    echo "size_bytes=$(stat -f%z "$signed")"
    echo "sha256=$sha"
    echo "package=$(echo "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
    echo "version=$(echo "$badging" | sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p") ($(echo "$badging" | sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p"))"
    echo "min_sdk=$(echo "$badging" | sed -n "s/^minSdkVersion:'\(.*\)'/\1/p")"
    echo "abis=$(echo "$badging" | sed -n "s/^native-code: //p" | tr -d "'")"
    echo "signing=signed, release key certificate SHA-256 $cert"
    grep -v '^version=' "$src/build.info"
  } > "$signed.info"
done
cp THIRD_PARTY_NOTICES.md "$out/THIRD_PARTY_NOTICES.md"

echo
echo "Signing certificate SHA-256: $cert"
echo
echo "Ready in $out (paste docs/RELEASE_NOTES_$version.md into the GitHub release):"
ls -1 "$out"
