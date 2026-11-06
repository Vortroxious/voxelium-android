#!/bin/zsh
# Signs the release APKs in build/release-<version>/ with your own release key and puts the signed
# files, with the release notes and their SHA-256 checksums, in ~/Desktop/Voxelium-<version>/.
#
# The key lives outside the repo (default ~/.voxelium/voxelium-release.jks; set VOXELIUM_KEYSTORE to
# use another). If it doesn't exist yet, it is created first. The password is typed by you and never
# saved. Keep the keystore and its password safe: every future release must be signed with it, or
# Android won't install the update over the old version.
#
# Usage (from the repository root): tools/sign_release.sh 0.3.0
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

cp "$src/RELEASE_NOTES.md" "$out/RELEASE_NOTES.md"
{
  echo
  echo "### SHA-256"
  echo '```'
  (cd "$out" && shasum -a 256 Voxelium-"$version"-*.apk)
  echo '```'
} >> "$out/RELEASE_NOTES.md"

echo
echo "Signing certificate:"
"$apksigner" verify --print-certs "$out/${apks[1]:t}" | grep -E "SHA-256 digest" | head -1
echo
echo "Ready in $out:"
ls -1 "$out"
