# Sado: Privacy Policy

Last updated: 18 November 2026

Sado is a personal workout app. It's made by an individual developer and shared with a small group of friends through Google Play internal testing. This policy explains what the app does with your data.

## The short version

- Everything you log stays on your phone. Sado has no account, no server and no cloud backup of its own.
- Sado has no ads, no analytics and no tracking.
- Data leaves your phone only when you use a feature that calls an outside service (listed below). Only what that feature needs is sent.

## Data stored on your phone

The app keeps the following in its private storage on your device:

- your profile: name, age, height, weight and goals
- your plan, workout logs, sport logs, food logs, body weight, streaks and progress
- heart-rate and activity data read from Health Connect or a connected watch

Uninstalling the app deletes all of this. You can export a backup file (.sado) yourself; that file goes only where you choose to put it.

## Features that contact outside services

| Feature | Service | What is sent |
|---|---|---|
| AI food logging | Anthropic (Claude API) | the food text you type, and the photo if you take one |
| Voice coach (optional) | ElevenLabs | the coaching lines to be spoken; for a voice clone, your recorded voice samples |
| Barcode lookup | Open Food Facts | the scanned barcode number |
| Prayer times (Ramadan mode) | JAKIM e-Solat | a fixed zone code, no personal data |
| Exercise pictures | wger.de, GitHub (free-exercise-db) | requests for public exercise images |

AI food logging and the voice coach use an API key that you enter yourself. The key is stored encrypted with the Android Keystore and is never included in exported backups. Each service handles the data it receives under its own privacy policy.

## Permissions

- **Camera:** to photograph food and to scan barcodes. Images are processed on the phone. A food photo is sent to Anthropic only when you ask for an AI estimate.
- **Microphone:** only for the optional voice clone. You record a few lines of your own voice, and they're sent to ElevenLabs to make a coaching voice that sounds like you. The recordings stay in the app's cache. You can delete them, and the cloned voice from your ElevenLabs account.
- **Bluetooth and location:** to find and connect to a heart-rate watch. Android requires the location permission for Bluetooth scanning. Sado doesn't read or store your location.
- **Health Connect (read only):** steps, exercise, heart rate, resting heart rate, sleep and calories burned. This data is used on the phone only, to show your progress and adjust your plan. It isn't sent anywhere, used for ads or sold.
- **Notifications:** for workout reminders.

## Children

Sado isn't directed at children under 13. Users under 18 are shown a safety notice before they start a plan.

## Changes

If this policy changes, the new version will be posted at this address with a new date.

## Contact

Questions about this policy: faiqkader@gmail.com
