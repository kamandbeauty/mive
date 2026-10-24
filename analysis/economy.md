# Economy / monetization snapshot — `com.dysugar.jewels.prince`
- APK: `jewels-classic-5.3.6.apk` — 61,134,581 B, sha256 `659aabcbe6bec0dc…`
- Version: 5.3.6 (537), minSdk 21 / targetSdk 33
- Code: 6 dex / 45,048,264 B, 56,456 classes, native 37,124,624 B
## Billing / ad keys declared in the manifest
- `com.google.android.gms.ads.APPLICATION_ID` = `ca-app-pub-3400181948465805~7823297437`
- `com.facebook.sdk.ApplicationId` = `1662725234017570`
- `com.facebook.sdk.ClientToken` = `e3edf00fb0adef2b96257e6cb6c370e1`
- `applovin.sdk.key` = `E8pVhU9mykQd3y0TD0…BdtQGH`
- `com.google.android.gms.ads.AD_MANAGER_APP` = `true`
- `aps.id` = `1f17cb36-29c4-4404-91cf-867aeb98bdbf`
- `smaato.appId` = ``
- `com.google.android.gms.ads.flag.OPTIMIZE_INITIALIZATION` = `true`
- `com.google.android.gms.ads.flag.OPTIMIZE_AD_LOADING` = `true`
- `com.google.android.play.billingclient.version` = `6.0.1`
- `com.android.stamp.source` = `https://play.google.com/store`
- `com.android.stamp.type` = `STAMP_TYPE_STANDALONE_APK`

## Ad & monetization SDK footprint (classes)
| SDK | classes |
|---|---:|
| Yandex Ads (divkit) | 6,306 |
| Yandex Mobile Ads | 4,161 |
| Mintegral | 2,924 |
| Firebase (analytics) | 2,602 |
| AppLovin MAX | 2,563 |
| Pangle / ByteDance | 2,110 |
| AppMetrica (analytics) | 1,800 |
| Fyber / Digital Turbine | 1,569 |
| AiHelp (support desk) | 1,481 |
| Smaato | 1,200 |
| AdMob / GMA | 984 |
| Unity Ads | 709 |
| IAB OM SDK (viewability) | 690 |
| ironSource / LevelPlay | 661 |
| Vungle | 621 |
| Pangle (ByteDance) | 488 |
| AppsFlyer (attribution) | 310 |
| Chartboost | 269 |

## Bundled economy configuration (25 files)
- `assets/data/beginner_task.json` — show=False
- `assets/data/custom_mall.json` — show=False
- `assets/data/deletion.json` — show=True
- `assets/data/diamond_tv_extravideo.json` — show=True
- `assets/data/diamond_tv_video.json` — times=5, cd=5
- `assets/data/endless.json` — show=False, unlock_level=50
- `assets/data/pre_level_video.json` — show=True, times=1, level=9
- `assets/data/race.json` — show=False
- `assets/data/retry_level_video.json` — show=True, times=1, level=9
- `assets/data/revival_gift.json` — show=True
- `assets/data/show_exitinter.json` — show=False, times=5
- `assets/data/show_homecustom.json` — show=False, times=60
- `assets/data/show_inter_fail.json` — show=True, times=1, level=7
- `assets/data/show_inter_victory.json` — show=True, times=1, level=7
- `assets/data/show_openinter.json` — show=False, times=5
- `assets/data/sign_video.json` — show=True
- `assets/data/soft_network.json` — show=True, unlock_level=10
- `assets/data/streak.json` — show=True, level=45
- `assets/data/super_light_ball.json` — show=False, unlock_level=50
- `assets/data/user_mapspin.json` — times=5, cd=1
- `assets/data/user_specialoffer.json` — show=False, price=0.99, payId=500
- `assets/data/user_videoshop.json` — show=False
- `assets/data/user_winspin.json` — (no scalar knobs)
- `assets/data/value_off.json` — show=False
- `assets/data/victory_double_video.json` — show=True, times=1

## Level economy
- `assets/levels/dailyevent_level.tsv`: 35 levels; moves min/median/max 18/24/28; 3★ score 3,500–30,000 (median 4,000)
  - moves by level band: 1-500: 24
- `assets/levels/levelinfo.tsv`: 4,210 levels; moves min/median/max 11/30.0/64; 3★ score 0–160,000 (median 18,750)
  - moves by level band: 1-500: 36, 501-1000: 36, 1001-1500: 32, 1501-2000: 32, 2001-2500: 27, 2501-3000: 30, 3001-3500: 30, 3501-4000: 28, 4001-4500: 28
- `assets/levels/weekevent_level.tsv`: 75 levels; moves min/median/max 20/27/34; 3★ score 9,000–40,000 (median 20,000)
  - moves by level band: 1-500: 27

## Price points baked into the binary
- prices: $0.99, $1.99, $2.99, $4.99, $6.99, $15.99, $49.99
- discount labels: 70%×10
- copy samples: `OFF`; `OFF`; `Remove ADs Forever`; `Double the prize!`; `OFF`; `Add 5 Lives`

## Ad placements referenced by the game code
- `RewardAD_ADRevival`
- `RewardAD_DailyGift`
- `RewardAD_DailyGift_failure`
- `RewardAD_DoubleAD`
- `RewardAD_EndLess_Video`
- `RewardAD_EventADEnter`
- `RewardAD_FreeTowerEnergy`
- `RewardAD_Lose_Video`
- `RewardAD_RunTurntable`
- `RewardAD_SingInDouble`
- `RewardAD_WatchAD`
- `RewardAD_WatchAD_Form`
- `RewardAD_WatchAD_MainMenu`
- `RewardAD_WatchAD_Shop`
- `RewardAD_WatchAD_StandBy`
- `RewardAD_WatchAD_VideoShop1`
- `RewardAD_WatchAD_VideoShop2`
- `RewardAD_WatchAD_VideoShop3`
- `RewardAD_WinCoin`
- `RewardAD_WinCoin_Tips`
- `RewardAD_WinCoin_Turntable`
- `RewardAD_WinCoin_Turntable_failure`
- `closeDeliciousBannerAd`
- `closeDeliciousIconAd`
- `hasDeliciousBannerAd`
- `hasDeliciousIconAd`
- `hasRewardAd`
- `showAds`
- `showDeliciousBannerAd`
- `showDeliciousIconAd`
- `showFullAd`
- `showRewardAd`
- `show_AD_Revival`
- `show_AD_Revival_InTower`
- `show_Double_AD`
- `show_ad_free`

## Backend hosts (non-SDK)
- `match3games1.iibingo.com` (4)
- `www.ivymobileinternational.com` (4)
- `levelstatistics.iibingo.com` (2)
- `sanxiao.iibingo.com` (2)
- `puzzle.papermobi.com:8080` (2)
- `api.aseogames.com` (2)
- `ip-api.com` (2)

> Static extraction only. Nothing here was executed on a device; server-side configuration can change every value above.
