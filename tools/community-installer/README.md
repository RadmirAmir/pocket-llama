# Community installation diagnostics

This small standalone Android app installs the **exact published community 0.9.0 APK** through Android's PackageInstaller API and displays its detailed result. It is useful when a file manager shows only “App not installed.”

**[Download Pocket Installer · 17 KB](https://github.com/RadmirAmir/pocket-llama/releases/download/v0.9.0-rc.1/Pocket-community-installer-1.0.apk)** · [SHA-256](https://github.com/RadmirAmir/pocket-llama/releases/download/v0.9.0-rc.1/Pocket-community-installer-1.0.apk.sha256)

1. Install this small helper. If Android asks, allow it to install apps, then return to it.
2. Tap **«Выбрать community APK»** and select the already downloaded `PocketLlama-0.9.0-community-arm64.apk`.
3. Confirm Android's installation dialog. If Android refuses, tap **«Копировать результат»** and send that diagnostic text to the maintainer.

The helper UI is currently Russian. It does not require a computer or another download of the full Pocket APK. Keep existing Pocket installations and their vaults while diagnosing a conflict.

It first verifies the full APK size and SHA-256 against the release. It reports the installed public package's version and signing-certificate hash, plus Android's result code/message. It requests installation permission and a user-selected APK through the system file picker. It has **no Internet permission, no vault access, no uninstall action and no automatic reporting**. The user can explicitly copy the diagnostic text.

The helper package is `dev.pocketllama.installer`; the app it installs remains the standard **community** package `dev.pocketllama`. The personal app is not involved. This is a diagnostic tool, not another Pocket distribution channel.

The [Android 16 emulator check](https://github.com/RadmirAmir/pocket-llama/actions/runs/39142020180) passed fresh installation, app startup, upgrade from community 0.8.0, the helper's interactive install/result callback and rejection of a corrupt APK. The image uses ARM translation on x86_64; this is not physical Samsung acceptance. The installation tests refuse to reset packages on a physical device.

## Build

Use Java 17, Android SDK 36 and build-tools 36.0.0. The script verifies its checksum-pinned Eclipse compiler; no Gradle or AndroidX runtime is needed.

```bash
export POCKET_INSTALLER_PASSWORD='<signing-key-password>'
python3 tools/community-installer/build.py \
  --build-tools "$ANDROID_HOME/build-tools/36.0.0" \
  --android-jar "$ANDROID_HOME/platforms/android-36/android.jar" \
  --keystore /secure/path/installer.keystore
```

Keep the signing key outside the repository. The source is covered by the repository's Apache-2.0 license. The compiler remains EPL-2.0 and is only a host build dependency; it is not packaged into the APK.
