# TODO

## Release and Distribution Safety

- [x] Disable app data backup and device transfer.
  - Set `android:allowBackup="false"` in `app/src/main/AndroidManifest.xml`.
  - Exclude all app-private data in `app/src/main/res/xml/backup_rules.xml`.
  - Exclude all cloud backup and device-transfer data in `app/src/main/res/xml/data_extraction_rules.xml`.
  - New devices require setup again instead of restoring weight state or Grip Gains session data.

- [x] Replace debug signing for release builds.
  - Removed `signingConfigs.getByName("debug")` from the `release` build type.
  - Use the `upload` alias in `~/keystores/upload-keystore.jks`.
  - Read signing passwords from `UPLOAD_KEYSTORE_PASSWORD` and `UPLOAD_KEY_PASSWORD` Gradle properties or environment variables.

- [x] Enable release minification and optimization.
  - Set `isMinifyEnabled = true` for the release build.
  - Use the default optimized Android ProGuard/R8 rules plus `app/proguard-rules.pro`.

## Session and Privacy Handling

- [x] Clear WebView cookies when the user clears the saved sign-in.
  - `clearGripGainsSession()` clears the encrypted app session.
  - `DefaultAuthRepository.clearSession()` also clears Grip Gains cookies through `CookieManager`.

- [x] Remove health data from release logs. - Don't care about this
  - Stop logging the submission JSON body that contains date and weight.
  - Stop logging server response bodies unless the build is debug-only.
  - Keep auth header and cookie values out of logs.

- [x] Narrow Health Connect reads.
  - Query only the last 24 hours of weight records.
  - Keep the existing current-day filter before auto-sync submission.

## Open Source Readiness

- [x] Decide whether the Grip Gains API contract should be public. -- Its ok
  - The repo exposes the production endpoint, auth header format, cookie name, origin, and referer.
  - If the API is private, avoid publishing this code as public open source.
  - If the API is intended for clients, document the supported integration path.

- [x] Remove tracked IDE metadata that does not belong in a public repo.
  - Keep stable Android Studio project files tracked.
  - Ignore local `.idea/` state such as caches, workspace, device target selection, and run-configuration producer preferences.

- [x] Keep generated local files and secrets out of version control.
  - Confirmed `local.properties` remains ignored.
  - Ignore keystores, `.env` files, `google-services.json`, PEM files, private keys, and common secret or credential filenames.
