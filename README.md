# Outlook Web Fit

<p align="center">
  <img src="docs/icon.svg" width="112" alt="Outlook Web Fit icon">
</p>

<p align="center">
  A focused Android wrapper that makes desktop Outlook on the web usable on a phone.
</p>

<p align="center">
  <a href="../../releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/ArchUserDesu/outlook-web-fit?display_name=tag&sort=semver"></a>
  <img alt="Android 6.0+" src="https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white">
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/badge/license-MIT-blue.svg"></a>
</p>

Outlook Web Fit is for accounts that work in a desktop browser but cannot be used in the Outlook Android app. It identifies as desktop Chrome while laying Outlook out at the phone's actual width, then removes space-hungry desktop chrome so the inbox and reading pane fit the screen.

> [!NOTE]
> This is an independent project. It is not affiliated with, endorsed by, or supported by Microsoft. Outlook and Microsoft are trademarks of Microsoft Corporation.

## Features

- Desktop-browser identity with a phone-width viewport for Outlook on the web
- Separate, persistent browser sessions for multiple mailboxes
- Isolated cookies and site storage for every supported session
- Add, switch, rename, and delete sessions from a collapsible drawer
- Adjustable Outlook text scale from 80% to 180%
- Full-screen interface with controls positioned around display cutouts
- Address/search field for opening other websites; non-Outlook pages retain normal desktop layout
- File uploads, authenticated downloads, back navigation, and reload controls
- No ads, analytics, account proxy, or custom network service
- Outlook-only clipboard bridge gated by a recent touch gesture

## Download

Download `OutlookWebFit.apk` from the [latest GitHub release](../../releases/latest). Android may ask you to allow installs from your browser or file manager.

The APK requires Android 6.0 (API 23) or newer. Separate sessions rely on Android System WebView's multi-profile support; keep System WebView updated for the best results.

The Android package name is `com.desudesu.outlookwebfit`. Releases before v2.2.0 used `com.local.outlookwebfit`; Android treats the new identifier as a separate app, so sessions from older installations do not migrate automatically.

Release checksums are included in the release notes. Upgrades must be signed with the same certificate, so APKs built with another keystore may require uninstalling the existing app first.

## How it works

The app loads `https://outlook.office.com/mail/` in Android WebView with a desktop Chrome user agent. For Outlook domains only, it applies a device-width viewport and narrow-screen CSS adjustments. Other pages keep a conventional desktop WebView layout.

Each extra mailbox uses an AndroidX WebKit profile. A profile owns its cookies and web storage, allowing accounts to remain signed in simultaneously. Deleting a session also requests deletion of that profile's browser data.

Credentials are entered directly into Microsoft's pages. The app does not read, store, transmit, or proxy passwords. Cookies remain in WebView's private application storage.

Android WebView does not provide Outlook with the desktop Clipboard API it expects. The app supplies a narrowly scoped compatibility bridge only to HTTPS Outlook origins. Clipboard reads require a touch inside the WebView within the previous five seconds; arbitrary sites opened from the address bar cannot access the bridge.

## Build in Termux

The project intentionally uses the Android command-line tools directly and does not require Gradle or Android Studio. SDK files are shared through `${ANDROID_SDK_ROOT:-$HOME/android-sdk}`, so multiple projects can reuse one installation.

```sh
pkg install aapt2 apksigner d8 openjdk-21 curl unzip zip
sh setup-toolchain.sh
sh build.sh
```

The signed APK is written to `OutlookWebFit.apk` and, when Android shared storage is available, copied to `/storage/emulated/0/Download/OutlookWebFit.apk`.

The default build creates a local development keystore that is ignored by Git. For controlled signing, provide these environment variables:

```sh
KEYSTORE_PATH=/path/to/release.keystore \
KEYSTORE_PASSWORD='your-password' \
KEY_ALIAS='your-alias' \
sh build.sh
```

## Project layout

```text
AndroidManifest.xml                            App permissions and metadata
res/drawable/ic_launcher.xml                   Vector launcher icon
src/com/desudesu/outlookwebfit/MainActivity.java  Browser UI and behavior
src/androidx/.../PackageInfoCompat.java        Small AndroidX compatibility shim
setup-toolchain.sh                             Prepares the shared SDK if needed
build.sh                                       Compiles, packages, and signs the APK
```

## Privacy and limitations

- Web content, sign-in, and mail traffic go directly between Android WebView and the websites you visit.
- Microsoft may change Outlook's web interface at any time, which can temporarily affect the layout adjustments.
- Authentication remains subject to your organization's Microsoft policies.
- This is a focused WebView wrapper, not a native mail client: there is no offline mailbox sync or background push service.
- Only install APKs from a source you trust. Review the source and checksum when possible.

## Contributing

Bug reports and focused pull requests are welcome. When reporting a layout issue, include the Android version, Android System WebView version, affected URL, and a screenshot with all personal mail information removed.

## License

[MIT](LICENSE)
