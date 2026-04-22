# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-04-21

Initial release of GravityGainsAssist.

### Added

- Read the latest bodyweight entry from Android Health Connect.
- Request Health Connect weight access, with optional background-read support when the device allows it.
- Sign in to Grip Gains from an in-app WebView and save the authenticated session for future syncs.
- Submit the latest weight to Grip Gains, converting kilograms to pounds and rounding to one decimal place.
- Run a manual sync on demand from the app.
- Support automatic background sync checks after setup so today's weight can be sent without reopening the app.
- Show current sync state in the app, including last weight read, last successful sync, skipped-sync reasons, and failure messages.
- Send notifications for automatic sync success and failure outcomes.
- Store app state locally and protect saved Grip Gains credentials with Android Keystore-backed encryption.

### Notes

- `minSdk` is 26, and the app targets Android SDK 36.
- The app requires Health Connect availability and a Grip Gains account.
