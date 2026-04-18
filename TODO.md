# Publish Readiness TODO

## High Priority

- [x] Add a top-level `README.md` that explains what the app does, how to build it, and how to run tests.
- [ ] Add a top-level `LICENSE` before making the repository open source.
- [x] Document privacy/data-use details in `README.md`, including what Health Connect data is read, what is stored locally, what is sent to Grip Gains, and how users can revoke permissions or clear their saved sign-in.
- [x] Document release signing prerequisites for private distribution, including required keystore location or override, key alias, and `UPLOAD_KEYSTORE_PASSWORD` / `UPLOAD_KEY_PASSWORD` configuration.
- [ ] Decide whether release signing should fail only for release builds with a clear error message, or support a documented local signing fallback.

## Medium Priority

- [x] Replace the Health Connect permissions rationale placeholder copy with final user-facing text.
- [x] Make the permissions rationale explicit that the app reads weight data from Health Connect and syncs it to Grip Gains.
- [ ] Confirm whether `minSdk = 36` is intentional. If it is, document the Android version requirement; if not, lower it to the oldest supported API level.
- [x] Audit tracked IDE metadata, keep shared project-opening files, and untrack local run/deployment state.
- [x] Update `.gitignore` so future local `.idea` run/deployment state is not committed accidentally.

## Low Priority

- [x] Harden the Grip Gains sign-in WebView by restricting navigation to expected Grip Gains hosts.
- [x] Disable unneeded WebView file/content access settings during sign-in.

## Verification Notes

- [x] `./gradlew test` passed during the review.
- [ ] `./gradlew assembleRelease` needs signing credentials before it can package a release artifact.
