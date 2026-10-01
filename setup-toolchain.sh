#!/data/data/com.termux/files/usr/bin/sh
set -eu

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
PLATFORM_DIR="$SDK_ROOT/platforms/android-35"
DOWNLOAD_DIR="$SDK_ROOT/downloads"
PLATFORM_ZIP="$DOWNLOAD_DIR/platform-35_r02.zip"
WEBKIT_DIR="$SDK_ROOT/extras/androidx/webkit/1.17.1"
WEBKIT_AAR="$WEBKIT_DIR/webkit.aar"

mkdir -p "$PLATFORM_DIR" "$DOWNLOAD_DIR" "$WEBKIT_DIR"

if [ ! -f "$PLATFORM_DIR/android.jar" ]; then
  curl -fL --retry 3 \
    https://dl.google.com/android/repository/platform-35_r02.zip \
    -o "$PLATFORM_ZIP"
  printf '%s  %s\n' \
    0bb560a90a7a2cbd0dd8348224d518b638fe7949 "$PLATFORM_ZIP" | sha1sum -c -
  unzip -p "$PLATFORM_ZIP" android-35/android.jar > "$PLATFORM_DIR/android.jar"
fi

if [ ! -f "$WEBKIT_DIR/classes.jar" ]; then
  curl -fL --retry 3 \
    https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/1.17.1/webkit-1.17.1.aar \
    -o "$WEBKIT_AAR"
  printf '%s  %s\n' \
    29dc7a9a32614828c97cf85951eb43c9e455157ab411e5e77d2ca4e72da28a36 "$WEBKIT_AAR" | sha256sum -c -
  unzip -p "$WEBKIT_AAR" classes.jar > "$WEBKIT_DIR/classes.jar"
fi

printf 'Shared Android SDK ready in %s\n' "$SDK_ROOT"
