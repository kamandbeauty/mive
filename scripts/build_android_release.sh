#!/usr/bin/env bash
set -Eeuo pipefail

# Release ad identifiers are intentionally not stored in source control. If they
# are omitted, rewarded ads are disabled and the offline game remains usable.
flutter_args=(build apk --release --split-per-abi)
for key in \
  ADIVERY_APP_ID \
  ADIVERY_REWARDED_PLACEMENT_ID \
  ADIVERY_REWARDED_COINS_PLACEMENT_ID \
  ADIVERY_REWARDED_EXTRA_MOVES_PLACEMENT_ID \
  ADIVERY_REWARDED_BOOSTER_PLACEMENT_ID \
  ADIVERY_REWARDED_DAILY_DOUBLE_PLACEMENT_ID \
  ADIVERY_REWARDED_MAP_CHEST_PLACEMENT_ID \
  AD_REWARD_COINS \
  AD_FREE_BOOSTER_DAILY_LIMIT; do
  value="${!key:-}"
  if [[ -n "$value" ]]; then
    flutter_args+=("--dart-define=$key=$value")
  fi
done

exec flutter "${flutter_args[@]}" "$@"
