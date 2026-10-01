#!/data/data/com.termux/files/usr/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
BUILD="$ROOT/build"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
ANDROID_JAR="$SDK_ROOT/platforms/android-35/android.jar"
WEBKIT_JAR="$SDK_ROOT/extras/androidx/webkit/1.17.1/classes.jar"
APK="$ROOT/OutlookWebFit.apk"
KEYSTORE="${KEYSTORE_PATH:-$ROOT/outlook-web-fit.keystore}"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-outlookfit}"
KEY_ALIAS="${KEY_ALIAS:-outlookwebfit}"

if [ ! -f "$ANDROID_JAR" ] || [ ! -f "$WEBKIT_JAR" ]; then
  printf 'Missing shared Android SDK. Run: sh setup-toolchain.sh\n' >&2
  exit 1
fi

mkdir -p "$BUILD/classes" "$BUILD/dex"
rm -f "$BUILD/app-unsigned.apk" "$BUILD/app-signed.apk" "$BUILD/dex/classes.dex" \
  "$BUILD/app-classes.jar" \
  "$BUILD/compiled-resources.zip" "$APK"

aapt2 compile --dir "$ROOT/res" -o "$BUILD/compiled-resources.zip"

aapt2 link \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --min-sdk-version 23 \
  --target-sdk-version 35 \
  --version-code 10 \
  --version-name 2.1.0 \
  "$BUILD/compiled-resources.zip" \
  -o "$BUILD/app-unsigned.apk"

javac -source 8 -target 8 -Xlint:-options \
  -classpath "$ANDROID_JAR:$WEBKIT_JAR" \
  -d "$BUILD/classes" \
  "$ROOT/src/com/local/outlookwebfit/MainActivity.java" \
  "$ROOT/src/androidx/core/content/pm/PackageInfoCompat.java"

jar cf "$BUILD/app-classes.jar" -C "$BUILD/classes" .
d8 --lib "$ANDROID_JAR" --min-api 23 --output "$BUILD/dex" \
  "$BUILD/app-classes.jar" "$WEBKIT_JAR"

cd "$BUILD/dex"
zip -q -j "$BUILD/app-unsigned.apk" classes.dex

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" \
    -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Outlook Web Fit, OU=Local, O=Local, L=Toronto, ST=Ontario, C=CA"
fi

apksigner sign \
  --ks "$KEYSTORE" \
  --ks-pass "pass:$KEYSTORE_PASSWORD" \
  --key-pass "pass:$KEYSTORE_PASSWORD" \
  --out "$APK" \
  "$BUILD/app-unsigned.apk"

apksigner verify --verbose "$APK"
sha256sum "$APK"
if [ -d /storage/emulated/0/Download ]; then
  cp "$APK" /storage/emulated/0/Download/OutlookWebFit.apk
  sha256sum /storage/emulated/0/Download/OutlookWebFit.apk
fi
