# Rewarded ads and the offline economy

## SDK and identifiers

The Android app uses the official [Adivery Flutter plugin](https://github.com/adivery/adivery-flutter-plugin) at tag `4.10.0-alpha.2`. The latest stable Flutter plugin tag inspected (`4.9.0`) restricts Dart to `<3.0.0`; the selected compatible tag is therefore an upstream pre-release and is Android-only. The plugin currently wraps Android SDK `4.10.0-alpha.1`.

Debug builds default to the public demo app and rewarded-placement IDs published in Adivery's [official Android sample](https://github.com/adivery/adivery-android-sample) (`d5247816-a170-4b90-b4e2-c0f1fcd804a4` and `271c8bd4-9000-476e-b096-6ef9bee296dc`). Release builds never fall back to those demo IDs. Supply real publisher-panel values as environment variables when building a release:

- `ADIVERY_APP_ID`
- either `ADIVERY_REWARDED_PLACEMENT_ID` or use-case-specific `ADIVERY_REWARDED_COINS_PLACEMENT_ID`, `ADIVERY_REWARDED_EXTRA_MOVES_PLACEMENT_ID`, `ADIVERY_REWARDED_BOOSTER_PLACEMENT_ID`, `ADIVERY_REWARDED_DAILY_DOUBLE_PLACEMENT_ID`, and `ADIVERY_REWARDED_MAP_CHEST_PLACEMENT_ID`
- optionally `AD_REWARD_COINS` and `AD_FREE_BOOSTER_DAILY_LIMIT`

Build with `scripts/build_android_release.sh`. Missing app or placement IDs leave ads disabled; the game, local wallet, daily rewards, and map remain usable offline.

## Consent and callback rules

The Adivery SDK is not initialized until the player opts in under **Settings → Adivery rewarded ads**. The choice is stored locally. The SDK's `onRewardedClosed(placement, isRewarded)` callback is authoritative: only `isRewarded == true` reaches the economy repository. Dismissal, load/show errors, missing connectivity, an unready placement, and duplicate callbacks never mint a reward. Each claim has an idempotency ID; daily double, free-booster caps, extra moves per level, and milestone chests also have persistent one-time checks.

Adivery's Flutter wrapper has no per-ad native destroy/shutdown method. Revoking consent blocks new game-side requests and discards pending claims; restart the app to fully stop the already-initialized native SDK. Publish and maintain the app's privacy policy and regional disclosures before store release; this opt-in text is not a substitute for legal review or a complete GDPR consent-management platform.

## Offline economy limits

Coins, boosters, daily rewards, milestone chests, and ad-claim receipts are stored on-device. Daily rewards use the device's local calendar date and intentionally do **not** claim to prevent clock tampering. This is a casual, offline economy, not a server-verified or fraud-resistant balance.

## Verification

`RewardedAdService` is tested behind an injectable client for success, unearned close, SDK failure, unavailable inventory, duplicate callbacks, repeated claim IDs, timeout, and consent revocation. Repository tests cover persistent/idempotent payments, the daily ad cap, one-time extra moves and chests, daily reward doubling, and purchases. CI resolves the official plugin, analyzes and tests Flutter code, builds split APKs, and runs the installed release APK on Google's Android emulator. A live ad impression still requires an internet-connected device and the relevant Adivery placement to serve an ad; CI does not simulate a paid impression.
