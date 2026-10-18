# Community installation diagnostics

This small standalone Android app installs the **exact published community 0.9.0 APK** through Android's PackageInstaller API and displays its detailed result. It is useful when a file manager shows only “App not installed.”

It first verifies the full APK size and SHA-256 against the release. It reports the installed public package's version and signing-certificate hash, plus Android's result code/message. It requests installation permission and a user-selected APK through the system file picker. It has **no Internet permission, no vault access, no uninstall action and no automatic reporting**. The user can explicitly copy the diagnostic text.

The helper package is `dev.pocketllama.installer`; the app it installs remains the standard **community** package `dev.pocketllama`. The personal app is not involved. This is a diagnostic tool, not another Pocket distribution channel.

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
