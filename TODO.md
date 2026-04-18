# TODO

## Release and Distribution Safety

- [ ] Disable app data backup, or explicitly exclude sensitive app data from backup and device transfer.
  - Review `android:allowBackup` in `app/src/main/AndroidManifest.xml`.
  - Exclude `grip_gains_session` shared preferences.
  - Exclude `files/datastore/app_state.preferences_pb`.
  - Update both `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml`.

- [x] Replace debug signing for release builds.
  - Removed `signingConfigs.getByName("debug")` from the `release` build type.
  - Use the `upload` alias in `~/keystores/upload-keystore.jks`.
  - Read signing passwords from `UPLOAD_KEYSTORE_PASSWORD` and `UPLOAD_KEY_PASSWORD` Gradle properties or environment variables.

- [x] Enable release minification and optimization.
  - Set `isMinifyEnabled = true` for the release build.
  - Use the default optimized Android ProGuard/R8 rules plus `app/proguard-rules.pro`.

## Session and Privacy Handling

- [ ] Clear WebView cookies when the user clears the saved sign-in.
  - `clearGripGainsSession()` clears the encrypted app session only.
  - Also clear the relevant `CookieManager` cookies used by the Grip Gains WebView.

- [ ] Remove health data from release logs.
  - Stop logging the submission JSON body that contains date and weight.
  - Stop logging server response bodies unless the build is debug-only.
  - Keep auth header and cookie values out of logs.

- [ ] Narrow Health Connect reads if possible.
  - The current query reads weight records from `Instant.EPOCH` through now.
  - Prefer a smaller time window if the app only needs the latest or today's weight.

## Open Source Readiness

- [ ] Decide whether the Grip Gains API contract should be public.
  - The repo exposes the production endpoint, auth header format, cookie name, origin, and referer.
  - If the API is private, avoid publishing this code as public open source.
  - If the API is intended for clients, document the supported integration path.

- [ ] Remove tracked IDE metadata that does not belong in a public repo.
  - Review tracked `.idea/*` files.
  - Keep only project files that are intentionally shared.

- [ ] Keep generated local files and secrets out of version control.
  - Confirm `local.properties` remains ignored.
  - Keep keystores, `.env` files, `google-services.json`, PEM files, and private keys untracked.
