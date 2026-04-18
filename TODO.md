# Publish Readiness TODO

## High Priority

- [ ] Add a top-level `README.md` that explains what the app does, how to build it, and how to run tests.
- [ ] Add a top-level `LICENSE` before making the repository open source.
- [ ] Add a privacy/data-use document that states what Health Connect data is read, what is stored locally, what is sent to Grip Gains, and how users can revoke permissions or clear their saved sign-in.
- [ ] Document release signing prerequisites for private distribution, including required keystore location or override, key alias, and `UPLOAD_KEYSTORE_PASSWORD` / `UPLOAD_KEY_PASSWORD` configuration.
- [ ] Decide whether release signing should fail only for release builds with a clear error message, or support a documented local signing fallback.

## Medium Priority

- [x] Replace the Health Connect permissions rationale placeholder copy with final user-facing text.
- [x] Make the permissions rationale explicit that the app reads weight data from Health Connect and syncs it to Grip Gains.
- [ ] Confirm whether `minSdk = 36` is intentional. If it is, document the Android version requirement; if not, lower it to the oldest supported API level.
- [ ] Remove tracked IDE metadata from the repository, especially `.idea/deploymentTargetSelector.xml`, `.idea/AndroidProjectSystem.xml`, and shared local run/deployment state.
- [ ] Update `.gitignore` so future local `.idea` state is not committed accidentally, while keeping only intentionally shared IDE config if needed.

## Low Priority

- [x] Harden the Grip Gains sign-in WebView by restricting navigation to expected Grip Gains hosts.
- [ ] Disable unneeded WebView file/content access settings during sign-in.

## Verification Notes

- [x] `./gradlew test` passed during the review.
- [ ] `./gradlew assembleRelease` needs signing credentials before it can package a release artifact.
