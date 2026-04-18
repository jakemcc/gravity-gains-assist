# GravityGainsAssist

GravityGainsAssist is an Android app that syncs bodyweight data from Health Connect to Grip Gains.
It is built with Kotlin, Jetpack Compose, WorkManager, DataStore, and the Android Health Connect client.

The app helps a Grip Gains user keep bodyweight records current without manually retyping weight entries.
After setup, it can read the latest Health Connect weight record, convert it to pounds, and submit it to Grip Gains.

## What It Does

- Shows the latest weight read from Health Connect.
- Requests Health Connect permission to read weight records.
- Lets the user sign in to Grip Gains through an in-app WebView.
- Captures the authenticated Grip Gains session needed to submit bodyweight data.
- Runs an immediate sync when the user taps **Run sync now**.
- Supports automatic background sync when Health Connect background reads are available and permitted.
- Reports sync status, skipped sync reasons, and sync failures in the app.

## Data Use

GravityGainsAssist reads weight data from Health Connect and sends bodyweight submissions to Grip Gains.

The app reads Health Connect `WeightRecord` data with the `READ_WEIGHT` permission.
Manual reads and normal syncs look for the latest weight record in the recent 24-hour window.
Automatic background sync uses today's Health Connect weight record when available.
The app does not write data back to Health Connect.

When submitting to Grip Gains, the app converts kilograms to pounds, rounds the value to one decimal place, and sends the data to `gripgains.ca`.

The app includes the saved Grip Gains authorization token and cookie header with the request so Grip Gains can accept the submission.

## Local Storage

GravityGainsAssist stores app state locally with Android DataStore.
Stored state includes:

- whether auto-sync is enabled;
- the latest weight value and timestamp read from Health Connect;
- Health Connect permission status;
- sync attempt and success timestamps;
- the next scheduled auto-sync check;
- the latest skipped sync reason or failure message;
- the last submitted date and weight.

Grip Gains sign-in data is stored separately in private app preferences.
The token and cookie header are encrypted with an Android Keystore-backed AES-GCM key.
Clearing the saved Grip Gains sign-in from the app removes the stored session and clears Grip Gains cookies managed by the app.

## Revoking Access

Turn off auto-sync in the app to stop scheduled background checks.

Use the app's clear sign-in action to remove the saved Grip Gains session and app-managed Grip Gains cookies.
After clearing the sign-in, the app cannot submit weight data until the user signs in again.

Revoke Health Connect access from Android settings.
Open Health Connect, choose app permissions, select GravityGainsAssist, and remove access.
After Health Connect access is revoked, the app cannot read weight records unless the user grants permission again.

Clear the app's storage or uninstall the app to remove locally stored app state.

## Network and Logs

The app uses network access only for Grip Gains sign-in and bodyweight submission.
It uses an in-app WebView for sign-in and restricts WebView navigation to `https://gripgains.ca` and its subdomains.

Submission logging records the request URL, JSON body, whether an authorization header is present, and cookie names.
It does not log the authorization token or cookie values.
Failed responses may log a truncated response body for debugging.

## Requirements

- Android Studio or the Android SDK command-line tools.
- JDK 21. The Gradle daemon JVM file is configured for Java 21 toolchains.
- Android SDK 36. The app currently uses `compileSdk` 36.1, `targetSdk` 36, and `minSdk` 26.
- A device or emulator running Android 8.0/API 26 or newer.
- Health Connect availability on the device.
- A Grip Gains account.

## Build

Build the debug APK:

```sh
make assemble
```

## Run

Install the debug APK on a connected device:

```sh
make install
```

If multiple devices are connected, pass the target serial:

```sh
make install SERIAL=<device-id>
```

Launch the installed app:

```sh
make run
```

## Test

Run unit tests:

```sh
make test
```

Run the standard local verification command:

```sh
make verify
```

## Release Builds

Release APK and bundle tasks are configured, but release signing requires local credentials.
The current release signing config expects:

- keystore path: `~/keystores/upload-keystore.jks`;
- key alias: `upload`;
- `UPLOAD_KEYSTORE_PASSWORD` from a Gradle property or environment variable;
- `UPLOAD_KEY_PASSWORD` from a Gradle property or environment variable.

Build a release APK:

```sh
make release
```

Build a release bundle:

```sh
make bundle-release
```

## Project Structure

- `app/src/main/java/com/jakemccrary/gravitygainsassist/health`: Health Connect access and permissions.
- `app/src/main/java/com/jakemccrary/gravitygainsassist/website`: Grip Gains sign-in, session storage, request creation, and submission.
- `app/src/main/java/com/jakemccrary/gravitygainsassist/sync`: manual sync, auto-sync planning, WorkManager scheduling, and failure notifications.
- `app/src/main/java/com/jakemccrary/gravitygainsassist/data`: local app state persistence.
- `app/src/main/java/com/jakemccrary/gravitygainsassist/ui`: Compose UI and screen state.
