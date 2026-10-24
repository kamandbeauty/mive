# تحلیل اقتصاد و درآمد — «Jewels Classic – Crush Jewels» (v5.3.6)

> تحلیل ایستا (static) روی فایل APK. هیچ بخشی از بازی اجرا نشده، هیچ ترافیک شبکهای گرفته نشده و هیچ سروری از سمت ما فراخوانی نشده است. هر عددی که در ادامه میآید یا مستقیماً از فایل استخراج شده (با ✅ مشخص شده) یا استنتاج تحلیلگر از کنار هم گذاشتن شواهد است (با 🔶).

---

## ۰. مشخصات فایل مورد بررسی

| مورد | مقدار |
|---|---|
| نام فایل | `Jewels Classic - Crush Jewels v5.3.6 Gameeroid.com.apk` |
| حجم | ۶۱٫۱۳۴٫۵۸۱ بایت (۵۸٫۳ مگابایت) |
| SHA-256 | `659aabcbe6bec0dc2274221409410cc0cb8cd80de4fea33391da62bb2f9f3ded` |
| پکیج | `com.dysugar.jewels.prince` |
| نسخه | 5.3.6 (versionCode 537) |
| minSdk / targetSdk | ۲۱ / ۳۳ |
| کد | ۶ فایل DEX، ۴۵٬۰۴۸٬۲۶۴ بایت، ۵۴٬۸۲۹ کلاس، ۲۰۹٬۳۳۳ رشته |
| کد بومی | ۳۷٬۱۲۴٬۶۲۴ بایت در دو ABI (`arm64-v8a`، `armeabi-v7a`) |
| امضا | v1 + v2 + v3 معتبر؛ گواهی `CN=SS`، SHA-256 گواهی `62c38b49…`، اعتبار ۲۰۱۶-۱۰-۱۴ تا ۲۱۱۶-۰۹-۲۰ |

✅ دو نکته مهم درباره اصالت بسته:

1. در مانیفست `com.android.stamp.source = https://play.google.com/store` و `com.android.stamp.type = STAMP_TYPE_STANDALONE_APK` و `com.android.vending.derived.apk.id = 2` ثبت شده است؛ یعنی این APK خروجی خودِ Google Play (از یک AAB) است، نه بازبستهبندی دستی یک سایت آینهای.
2. گواهی امضا از سال ۲۰۱۶ و با اعتبار ۱۰۰ ساله است و با تاریخ اولین انتشار اپ (اکتبر ۲۰۱۶) همخوانی دارد. 🔶 بنابراین احتمال «نسخهٔ مودشده» پایین است، ولی این موضوع را نمیتوان بدون مقایسه با گواهی رسمی Play تأیید کرد (Play از این سندباکس در دسترس نیست).

🔶 نکتهٔ تحلیلی: نسخهٔ آینهای ۵.۳.۶ است در حالی که نسخهٔ فعلی در Play، ۵.۷.۷ است؛ یعنی این تحلیل تصویری از اقتصاد بازی در حدود دو سال پیش است، نه وضعیت امروز.

---

## ۱. خلاصهٔ اجرایی (۵ خط)

- بازی یک Match-3 با موتور **Cocos2d-x** است که کل منطق اقتصادش داخل `libMyGame.so` کامپایل شده و کاتالوگ فروشگاهش **از سرور** میآید (`getPaymentDatas`)، نه از خود APK.
- درآمد از **سه موتور موازی** میآید: تبلیغات با واسطهگری چندشبکهای (AppLovin MAX + AdMob + IronSource/LevelPlay + Mintegral + Pangle + Yandex + Fyber + Smaato + Vungle + Unity Ads + Chartboost + Meta AN + Amazon APS)، خرید درونبرنامهای (Billing 6.0.1) و اقتصاد انرژی/جان.
- حدود **۲۲ جایگاه تبلیغ جایزهای (Rewarded)** و **۲ جایگاه میانبرنامهای (Interstitial)** در کد نامگذاری شدهاند؛ میانبرنامهای از سطح ۷ ویدیویی فعال میشود و تبلیغ پایانبازی با «دو برابر کردن جایزه» به ۱۰۰٪ بازیکنان پیشنهاد میشود.
- فروشگاه شامل ۵ بستهٔ سکه (`Payment_Coins0..4`)، حذف دائمی تبلیغات، و یک خانوادهٔ کامل از «گیفت»های تخفیفدار (FirstValue / FirstSuper / Super / Luxurious / Dazzling / Advanced / TimeLimit / Revival) با تخفیفهای ۷۰٪ و ۸۰٪ است.
- قواعد پیشفرضِ داخل APK نشان میدهد اقتصاد با **۴٬۲۱۰ سطح**، **۳۰ حرکت میانه**، چرخ شانس با احتمالهای صریح، بتلپس ۳۰ پله و «پیشنهاد شکار» بعد از عبور از N سطح تغذیه میشود.

---

## ۲. روش کار و محدودیتها

**چه چیزی واقعاً اجرا شد (✅):**

- `rea identify-flutter-build` → `flutter_detected: false` (بازی Flutter نیست؛ مسیر Dart منتفی شد).
- `tools/apk-triage.py` (androguard) → مانیفست، مجوزها، کامپوننتهای exported، امضا، ABI، کتابخانهها، نقشهٔ SDK و ۲۲۳ URL.
- `tools/apk-economy.py` (ابزار جدید این تحلیل) → استخراج تنظیمات اقتصادی JSON، جدول قیمتها، جایگاههای تبلیغ، کلیدهای اقتصادی `libMyGame.so`، آمار سطحها و هاستهای بکاند.
- بازکردن زیپ APK و خواندن مستقیم `assets/**` و رشتههای `libMyGame.so`.

**چه چیزی اجرا نشد (⚠️):**

- اپ روی دستگاه/امولاتور اجرا نشد؛ رفتار واقعی شبکه، نرخ پر شدن تبلیغ و eCPM اندازهگیری نشد.
- سرورهای بازی (`*.iibingo.com`، `*.papermobi.com`) از این محیط قابل دسترسی نیستند؛ پس **کاتالوگ واقعی فروشگاه و قیمتهای لحظهای دیده نشده**.
- ترافیک Google Play Billing شبیهسازی نشد؛ فهرست SKUهای واقعی در Play Console نامعلوم است.

**پیامد مهم:** هر قیمتی که در ادامه میبینید، «متن پیشفرض/قدیمی داخل بسته» است. در این معماری، قیمت نهایی از سرور و سپس از Play Billing میآید.

---

## ۳. معماری درآمد در یک نگاه

```
┌─────────────────────────────── libMyGame.so (Cocos2d-x, C++) ───────────────────────────────┐
│  UIFormShop / UIFormPropShop / UIFormVideoShop / UIFormGiftShop_1|2 / UIFormExtraPayment      │
│  UIFormWinCoin / UIFormStandby (Energy) / UIFormNewBattlePass / UIFormBuyTier                 │
│  payCallbackWithPayid(payId)        ADs::showRewardAd(key)        TimeSystem (lives regen)     │
└───────────────┬──────────────────────────────────┬───────────────────────────────┬────────────┘
                │ JNI                              │ JNI                           │ HTTP
     com.android.client.AndroidSdk        com.android.client.Cocos       match3games1.iibingo.com
     (پرداخت + مدییشن تبلیغ)              (نمایش تبلیغ)                  levelstatistics.iibingo.com
                │                                  │                        gateway-2.papermobi.com:8080
     Google Play Billing 6.0.1          AppLovin MAX / AdMob / ...       (کانفیگ و آفر)
```

✅ کلاس `com.android.client.AndroidSdk` تنها نقطهٔ اتصال JNI برای پرداخت و تبلیغات است؛ کل بازی از این یک پل عبور میکند. 🔶 این یعنی هرگونه دستکاری در این پل (یا در پاسخ سرور) مستقیماً روی درآمد اثر میگذارد.

---

## ۴. ستون اول: تبلیغات

### ۴.۱ انبار فناوری تبلیغاتی (✅ شمارش کلاسها در DEX)

| SDK | نقش | تعداد کلاس |
|---|---|---:|
| Yandex Mobile Ads + DivKit | شبکه + رندر بنر نیتیو | ۱۰٬۴۶۷ |
| Mintegral (mbridge) | شبکه + مدییشن | ۲٬۹۲۴ |
| AppLovin MAX | مدییشن اصلی (کلید در مانیفست) | ۲٬۵۶۳ |
| Pangle / ByteDance | شبکه + RASP و APM بومی (Metasec / APM Insight) | ۲٬۵۹۸ |
| Fyber / Digital Turbine | شبکه + مدییشن | ۱٬۵۶۹ |
| Smaato | شبکه | ۱٬۲۰۰ |
| AdMob / GMA | شبکه + Ad Manager | ۹۸۴ |
| Unity Ads | شبکه | ۷۰۹ |
| IAB OM SDK | اندازهگیری viewability | ۶۹۰ |
| ironSource / LevelPlay | مدییشن | ۶۶۱ |
| Vungle (Liftoff) | شبکه | ۶۲۱ |
| Chartboost | شبکه | ۲۶۹ |
| Meta Audience Network | شبکه (`assets/audience_network.dex`، ۳٫۲ مگابایت) | در DEX جدا |
| Amazon APS | شبکه (`aps.id` + `aps-mraid.js`) | — |

کلیدهای اعلامشده در مانیفست (✅):

```
com.google.android.gms.ads.APPLICATION_ID = ca-app-pub-3400181948465805~7823297437
applovin.sdk.key                          = E8pVhU9mykQd3y0TD0…BdtQGH
com.facebook.sdk.ApplicationId            = 1662725234017570
aps.id                                    = 1f17cb36-29c4-4404-91cf-867aeb98bdbf
smaato.appId                              = (خالی)
com.google.android.play.billingclient.version = 6.0.1
```

✅ علاوه بر کلاسهای جاوا، Pangle سه کتابخانهٔ بومی هم با خود آورده است:
`libPglmetasec_ov.so` (نماد `com/bytedance/mobsec/metasec/ov/PglMS` — لایهٔ RASP/ضد دستکاری)،
`libapminsighta.so` / `libapminsightb.so` (نقشهٔ `com/apm/insight` — پایش و کرش) و `libEncryptorP.so`.
🔶 یعنی نصب یک شبکهٔ تبلیغاتی، همزمان یک لایهٔ امنیتی و تلمتری سنگین را هم وارد اپ کرده است.

🔶 **تفسیر:** این پیکربندی، الگوی «waterfall + bidding» است: AppLovin MAX بهعنوان لایهٔ مدییشن، و شبکههای متعدد بهعنوان adapter. حضور همزمان AdMob و Ad Manager و Yandex و Pangle یعنی فروش موجودی بهصورت چندبازاری (multi-market) — انتظار میرفت نسخهٔ ایرانی/خاورمیانهای باشد، اما تمرکز شبکهها بیشتر روسیه/CIS (Yandex) و آسیای شرقی (Pangle/Mintegral) است.

### ۴.۲ جایگاههای تبلیغ (✅ از `libMyGame.so`)

**Rewarded (۲۲ کلید):** `RewardAD_WatchAD`, `_MainMenu`, `_StandBy`, `_Shop`, `_Form`, `_VideoShop1..3`, `RewardAD_WinCoin`, `_WinCoin_Turntable`, `_WinCoin_Tips`, `RewardAD_DoubleAD`, `RewardAD_ADRevival`, `RewardAD_Lose_Video`, `RewardAD_FreeTowerEnergy`, `RewardAD_EndLess_Video`, `RewardAD_RunTurntable`, `RewardAD_DailyGift`, `RewardAD_SingInDouble`, `RewardAD_EventADEnter`, و دو کلید شکست (`_failure`).

**Interstitial (۲ کلید فعال):** `show_inter_victory` (از سطح ۷، `times: 1`)، `show_inter_fail` (از سطح ۷، `times: 1`).
سه کلید `show_openinter`، `show_exitinter` و `show_homecustom` در این نسخه `show: false` هستند (✅) — یعنی تیم محصول عمداً از «تبلیغ هنگام باز شدن/خروج» صرفنظر کرده و آن را به بعد از برد/باخت منتقل کرده است.

**بنر/نیتیو:** `showBanner`, `showNativeBanner`, `showDeliciousBannerAd`, `showDeliciousIconAd` + قالب چیدمان اختصاصی `assets/config.json` (۴۶۰×…) و `assets/delicious/delicious-android.json`.

### ۴.۳ قواعد فرکانس و سقف (✅ پیشفرضهای بسته)

| فایل | مقدار | معنی |
|---|---|---|
| `assets/data/diamond_tv_video.json` | `times: 5, cd: 5` | ۵ ویدیو برای الماس در هر دورهٔ خنکشدن |
| `assets/data/pre_level_video.json` | `show: true, level: 9, times: 1` | یک ویدیو قبل از سطح، از سطح ۹ |
| `assets/data/retry_level_video.json` | `show: true, level: 9, times: 1` | یک ویدیو برای تلاش مجدد |
| `assets/data/victory_double_video.json` | `show: true, percent: 100, times: 1` | «دو برابر کردن جایزه» برای **۱۰۰٪** بازیکنان |
| `assets/data/revival_show.json` | `show_video_percent: 20` | فقط ۲۰٪ بازیکنان گزینهٔ احیای ویدیویی میبینند |
| `assets/data/show_inter_victory.json` / `_fail` | `level: 7, times: 1` | شروع میانبرنامهای از سطح ۷ |
| `assets/data/show_openinter/exitinter/homecustom` | `show: false` | غیرفعال در این نسخه |

🔶 **تفسیر:** «۱۰۰٪ برای دوبل کردن جایزه» یک انتخاب طراحی است، نه تصادف: بازیکن بلافاصله بعد از برد، بالاترین انگیزه را برای یک ویدیوی ۳۰ ثانیهای دارد و این جایگاه معمولاً بالاترین نرخ تکمیل را در Match-3 دارد. در مقابل، ۲۰٪ برای «احیای پس از شکست» نشانهٔ A/B تست محافظهکارانه است.

### ۴.۴ مدیایشن و لایهٔ واسط خودی

✅ نامهای `com.android.client.Cocos.showRewardAd(String)`, `hasRewardAd(String)`, `showFullAd(String)`, `showNativeBanner(...)` و `delicious` نشان میدهد سازنده یک لایهٔ خودی روی SDKها دارد و «delicious» نام داخلی سیستم تبلیغاتش است.

---

## ۵. ستون دوم: خرید درونبرنامهای

### ۵.۱ زیرساخت (✅)

- مجوز `com.android.vending.BILLING` و `com.google.android.play.billingclient.version = 6.0.1`.
- مسیر پرداخت در `libMyGame.so`: `payCallbackWithPayid(payId)`, `payGiftCallbackWithPayid(payId, …)`, `UIFormExtraPayment`, `PAYINFOSAVE`.
- کاتالوگ **سمت سرور** است: فیلدهای `getPaymentDatas`, `currency`, `price_amount`, `cgid`, `goods_id`, `goods_num`, `customkey`, `saedate`, `PayIDEnableRefreshDays`.

🔶 این طراحی یعنی سرور میتواند قیمت، موجودی بسته و حتی «چند روز یکبار پیشنهاد تازه نشان داده شود» را بدون آپدیت اپ عوض کند — توانایی بالای A/B تست قیمت.

### ۵.۲ تاکسونومی کالاها (✅ کلیدهای داخل باینری)

| گروه | کلیدها |
|---|---|
| بستهٔ سکه | `Payment_Coins0` … `Payment_Coins4`، `Payment_Coins_Discount70` |
| حذف تبلیغ | `Payment_Remove_AD`, `Payment_RemoveAD_Gift`, `UserDefault_isRemoveAd` |
| گیفتهای هدفمند | `Payment_FirstValueGift`, `Payment_FirstSuperGift`, `Payment_Value_Gift`, `Payment_Super_Gift`, `Payment_LuxuriousGift`, `Payment_DazzlingGift`, `Payment_AdvancedGift`, `Payment_TimeLimit_Gift` |
| احیا/بازگشت | `Payment_Revival_Gift`, `Payment_Revival_Gift2`, `Payment_Super_Revival_Gift`, `Payment_Super_Revival_Gift2` |
| حالت بیپایان | `Payment_EndLess`, `Payment_EndLess_Free` |
| فروشگاه سفارشی | `Payment_CustomMall`, `Payment_Gift_Discount80` |

🔶 الگوی `First…` + `…Gift` + `Discount70/80` یک استراتژی کلاسیک F2P است: **اولین خرید ارزان** برای شکستن سد روانی پرداخت، سپس «گیفت»های گرانتر با تخفیف ظاهراً بزرگ.

### ۵.۳ نردبان قیمت داخل بسته (✅ متنهای i18n)

```
$0.99  $1.99  $2.99  $4.99  $6.99  $15.99  $49.99      + برچسب "70% OFF" (۱۰ بار) + "9.99 → 2.99"
```

همراه با این متنها: `Remove ADs Forever`، `Add 5 Lives`، `Unlimited Lives for 2 hours`، `Double the prize!`، `Item shop`، `9999` (عدد مجاور قیمت ۴۹٫۹۹ — 🔶 احتمالاً اندازهٔ بستهٔ سکه یا سقف کیف پول؛ تأیید نشده).

### ۵.۴ آفرهای پیشفرض بستهٔ APK (✅ `assets/data/*.json`)

| فایل | payId | قیمت | تخفیف | محتوا |
|---|---:|---:|---:|---|
| `user_specialoffer.json` | 500 | $0.99 | ۸۰٪ | ۱۰۰ سکه + ۵ آیتم (typeId 1,2,3,5,6) |
| `value_off.json` | 509 | $2.99 | ۸۰٪ | ۵۰۰ سکه |
| `value_off.json` | 510 | $10.99 | ۸۰٪ | ۱٬۰۰۰ سکه + ۵×typeId 18 |
| `value_off.json` | 511 | $14.99 | ۸۰٪ | ۲٬۵۰۰ سکه |

🔶 محاسبهٔ قیمت واحد (استنتاج از اعداد بالا):

| بسته | $/سکه | قیمت «قبل از تخفیف» اگر ۸۰٪ واقعی باشد |
|---|---:|---:|
| ۱۰۰ سکه / $0.99 | ۰٫۰۰۹۹ | $4.95 |
| ۵۰۰ سکه / $2.99 | ۰٫۰۰۶۰ | $14.95 |
| ۱٬۰۰۰ سکه / $10.99 | ۰٫۰۱۱۰ | $54.95 |
| ۲٬۵۰۰ سکه / $14.99 | ۰٫۰۰۶۰ | $74.95 |

بستهٔ ۱۰٫۹۹ از نظر سکه بدترین ارزش را دارد؛ چون ۵ آیتم پرمیوم (typeId 18) همراهش است، احتمالاً عمداً «بستهٔ آیتممحور» طراحی شده نه «سکهٔ خالص». 🔶 اگر مصرفکننده فقط سکه بخواهد، این نردبان او را به سمت ۲٫۹۹ یا ۱۴٫۹۹ هدایت میکند.

⚠️ نکتهٔ انطباق: `value_off.json` میگوید `"compare_gift": false` و همهٔ آفرها `discount_percent: 80` با `show: false` هستند. یعنی تخفیف ۸۰٪ **بهصورت دائمی** روی همان بستهها نشسته و قیمت مرجع مقایسهای نمایش داده نمیشود. در اتحادیهٔ اروپا (دستور Omnibus) نمایش «تخفیف» بدون قیمت مرجع واقعی و حداقلی ۳۰ روزه، ریسک حقوقی دارد.

---

## ۶. ستون سوم: اقتصاد بازی (سکه، الماس، انرژی، بتلپس)

### ۶.۱ ارزها و آیتمها

- **سکه (typeId 0)** — ارز نرم؛ از چرخ، برد سطح، تبلیغ و خرید.
- **الماس** — ارز سخت؛ مسیرهای کسب: `diamond_tv_video` (۵ بار، هر ۵ دقیقه؟)، `diamond_tv_extravideo`، `diamond_spin`.
- **آیتمها/بوسترها (typeId 1,2,3,5,6,18)** — از `user_specialoffer`، `value_off`، `user_winspin` و فروشگاه آیتم؛ 🔶 نگاشت دقیق شناسهها به نام بوستر در باینری صریح نیست (کلیدهای `item1..item5`, `props4`, `props5`).

### ۶.۲ انرژی/جان

✅ `TimeSystem::mOneLifeTimeCount`، `Next Life in:`، `Life Shop`، `Add 5 Lives`، `Unlimited Lives for 2 hours`، `buy_TowerEnergy`، `RewardAD_FreeTowerEnergy`، `Tower_Energy_AD`، `ADEnergyTime`، `FlashEnergy`، `heart_use`.

🔶 دو مسیر پولسازی روی یک منبع: وقتی جان تمام شود، هم میتوان با تبلیغ جان گرفت و هم با پرداخت (`$1.99` برای ۵ جان یا ۲ ساعت بینهایت). این «دوبار فروش یک محدودیت» است.

### ۶.۳ چرخ شانس و قرعهکشی

| فایل | محتوا | احتمالها |
|---|---|---|
| `user_winspin.json` | ۵ سکه … ۵۰ سکه | ۱، ۲، ۵، ۲۱، ۵۸، ۱۳ → **جمع ۱۰۰** |
| `user_mapspin.json` | typeId 0/1/2/3/5/6، ۵ چرخ، `cd: 1` | ۱، ۴، ۳۰، ۳۳، ۷، ۵، ۱۵، ۲ → **جمع ۹۷** |
| `user_videoshop.json` | ۳ آیتم با `times` و `cd` | — |
| `diamond_spin`, `COIN_LOTTERY_TIMES`, `COIN_LOTTERY_PROGRESS`, `TurntableAdTimes` | چرخ الماسی/قرعهکشی سکه | — |

⚠️ جمع ۹۷٪ در `user_mapspin` یا یک «۳٪ شانس باخت» تعریفشده است یا خطای پیکربندی. چون این فایلها پیشفرضاند و سرور میتواند بازنویسی کند، بدون نسخهٔ سروری نمیشود قطعی گفت.

### ۶.۴ بتلپس و مأموریتها

✅ `battlepass.json`: ۳۰ پله (`lv` از ۱ تا ۳۰)، `season: 2`، دو ریل پاداش:
- ریل رایگان: `13-1`, `16-1,17-1`, `14-1`, `15-1` و سکههای `0-5/10/20/30/40/50`.
- ریل VIP: `1-1`, `5-1,6-1`, `2-1`, `3-1` و سکههای `0-10/20/40/60/80/100`.

✅ مأموریتها: `task_login`, `task_pass%d_levels`, `task_play%d_levels`, `task_create5_boosters`, `task_create10_special`, `task_collect100_{red,yellow,green,orange}`.
✅ استریک: `streak.json` (`level: 45, streak_stage: 5, moves: 5`)، `StreakNeedReduceMoves`, `streak_reduce_moves.json` (`reduce: 5`).

### ۶.۵ آفر هدفمند پس از پیشرفت

✅ `pursuegift`, `show_pursuegift`, `pursuegift_pass%d`, `Payment_TimeLimit_Gift`, `PayIDEnableRefreshDays`, `firstpurchase_passlevels`, `firstpurchase_days`, `iap_level_times`.

🔶 اینها یعنی: «بعد از عبور از N سطح، تا M روز یک آفر محدود نشان بده و اگر خرید نکرد، دوباره». این حلقهٔ retargeting درونبرنامهای، بهجای ارسال نوتیفیکیشن، داخل خود بازی اجرا میشود.

---

## ۷. اقتصاد سطح: ۴٬۲۱۰ سطح و منحنی سختی

✅ از `assets/levels/levelinfo.tsv` (۴٬۲۱۰ سطح) و دو جدول رویدادی (`dailyevent_level.tsv` ۳۵ سطح، `weekevent_level.tsv` ۷۵ سطح):

| بازهٔ سطح | میانهٔ حرکت | میانهٔ آستانهٔ ۳ ستاره |
|---|---:|---:|
| ۱–۵۰۰ | ۳۶ | ۲۰٬۰۰۰ |
| ۵۰۱–۱۰۰۰ | ۳۶ | ۲۰٬۰۰۰ |
| ۱۰۰۱–۱۵۰۰ | ۳۲ | ۲۰٬۰۰۰ |
| ۱۵۰۱–۲۰۰۰ | ۳۲ | ۲۰٬۰۰۰ |
| ۲۰۰۱–۲۵۰۰ | ۲۷ | ۱۵٬۰۰۰ |
| ۲۵۰۱–۳۰۰۰ | ۳۰ | ۱۵٬۰۰۰ |
| ۳۰۰۱–۳۵۰۰ | ۳۰ | ۱۵٬۰۰۰ |
| ۳۵۰۱–۴۰۰۰ | ۲۸ | ۲۰٬۰۰۰ |
| ۴۰۰۱–۴۲۱۰ | ۲۸ | ۱۸٬۰۰۰ |

- دامنهٔ حرکت: ۱۱ تا ۶۴؛ میانه ۳۰.
- انواع هدف (`Target` نوع ۲۰، ۱۰۰، ۳۰، ۱۳۰، ۴۱۰، ۴۰، ۱۵۱، ۶) — 🔶 نگاشت این شناسهها به «شکستن یخ/جمعآوری فنجان/زنجیر» از متن راهنمای i18n قابل حدس است ولی در فایل صریح نیست.
- 🔶 الگو: حرکات در نیمهٔ اول بیشتر و در نیمهٔ دوم کمتر میشود، در حالی که آستانهٔ امتیاز بالا میرود. این دقیقاً همان نقطهای است که بازیکن به «خرید حرکت اضافه» و «بوستر» نیاز پیدا میکند؛ یعنی **دیوار پرداخت قبل از دیوار مهارت** قرار گرفته است.
- ✅ آمار سطح به `http://levelstatistics.iibingo.com/api/Dot/` فرستاده میشود؛ 🔶 یعنی تیم محصول نرخ برد/باخت هر سطح را اندازه میگیرد و میتواند سختی را پشت سر هم تنظیم کند.

---

## ۸. اندازهگیری رفتار و قیف تبدیل (✅ رشتههای رویداد)

کلیدهای تحلیلی که مستقیماً به پول مربوطاند:

```
coins_get   coins_use   all_pay_num   all_payment
last_3d_payment  last_5d_payment  last_5d_pay_num  last_5d_pay_days
firstpurchase_passlevels  firstpurchase_days  iap_level_times
action,purchase_%d   action,free_%d   action,battlepass_%d
diamond_tv_video   diamond_spin   freevideo   passvideo
lose_showvideo   lose_showvideo_level   lose_showvideo_percent
VideoADS_Impressions   TurntableAdTimes   TowerAdAwardTime
```

🔶 تفسیر: تیم محصول «پنجرهٔ ۵ روزهٔ پرداخت» و «تعداد پرداخت» و «سطحی که کاربر اول خرید کرد» را نگه میدارد. این یعنی KPI اصلی، **نرخ تبدیل در ۵ روز اول** است و طراحی آفرها (FirstValue/FirstSuper) دقیقاً روی همان پنجره تنظیم شده.

---

## ۹. ریسکها و نقاط ضعف درآمدی

| # | یافته (✅) | ریسک (🔶) |
|---|---|---|
| ۱ | `usesCleartextTraffic = true` | کانفیگ آفر/تبلیغ میتواند روی HTTP دستکاری شود؛ `gateway-2.papermobi.com:8080`، `puzzle.papermobi.com:8080` و `match3games1.iibingo.com` همه HTTP هستند |
| ۲ | `UserDefault_isRemoveAd` در حافظهٔ محلی | حذف تبلیغ بدون اعتبارسنجی سرور قابل بازگردانی است (از دست رفتن درآمد) |
| ۳ | جدول احتمال چرخها در کلاینت | دستکاری مقدار احتمال یا نتیجه در دستگاه روتشده |
| ۴ | `price_amount` و `currency` از سرور | اگر امضای پاسخ نباشد، قیمت نمایشی قابل دستکاری است (فیشینگ قیمتی) |
| ۵ | تخفیف دائمی ۸۰٪ + `compare_gift: false` | ریسک انطباق با قوانین حمایت از مصرفکننده در EU/بریتانیا |
| ۶ | ۳۲ مجوز + `AD_ID` + AppsFlyer/AppMetrica | ریسک حریم خصوصی؛ در EU نیاز به رضایت صریح |
| ۷ | نسخهٔ آینهای ۵.۳.۶ در برابر ۵.۷.۷ در Play | تحلیل اقتصاد نسخهٔ قدیمی است؛ ممکن است نردبان قیمت تغییر کرده باشد |
| ۸ | `libPglmetasec_ov.so` (نماد `com/bytedance/mobsec/metasec/ov/PglMS`) و `libapminsighta/b.so` (`com/apm/insight`) | 🔶 اینها لایهٔ RASP و APM خودِ ByteDance/Pangle هستند که همراه SDK پنگل وارد بازی شدهاند: تشخیص روت/دیباگر و ارسال کرش. یعنی همراهی با Pangle، سطح دسترسی و حجم بستهٔ فزایندهای به اپ تحمیل میکند |

---

## ۱۰. شواهد در برابر استنتاج (خلاصه)

| ادعا | وضعیت |
|---|---|
| بستهٔ APK محصول Play است، نه بازبستهبندی | ✅ `STAMP_TYPE_STANDALONE_APK` + گواهی ۲۰۱۶ |
| بازی Cocos2d-x است | ✅ `libMyGame.so` + `org.cocos2dx.cpp.AppActivity` + مسیرهای `assets/*.plist/.atlas` |
| از AppLovin MAX بهعنوان مدییشن استفاده میشود | ✅ کلید `applovin.sdk.key` + ۲٬۵۶۳ کلاس AppLovin |
| ۲۲ جایگاه Rewarded وجود دارد | ✅ رشتههای `RewardAD_*` |
| میانبرنامهای از سطح ۷ فعال میشود | ✅ `show_inter_victory.json` |
| کاتالوگ فروشگاه سمت سرور است | ✅ فیلدهای `getPaymentDatas/price_amount/cgid` |
| قیمتهای ۰٫۹۹ تا ۴۹٫۹۹ واقعی و فعالاند | 🔶 متن پیشفرض i18n؛ قیمت واقعی از Play میآید |
| تخفیف ۸۰٪ «واقعی» است | ⚠️ نامعلوم؛ قیمت مرجع در بسته نیست |
| eCPM یا ARPDAU مشخص است | ⚠️ از فایل ایستا قابل محاسبه نیست |
| موتور مالی بازی «تبلیغاتمحور» است یا «IAPمحور» | 🔶 با شواهد موجود، هر دو موازی و سنگیناند؛ بدون دادهٔ اجرا نمیتوان سهم داد |

---

## ۱۱. اگر این بازی مال شماست (توصیههای عملی)

1. **کانفیگ را امضا کنید.** آفرها و قیمتها روی HTTP میآیند؛ یک HMAC روی پاسخ سرور، جلوی دستکاری قیمت و آفر را میگیرد.
2. **حذف تبلیغ را سمت سرور نگه دارید.** `UserDefault_isRemoveAd` محلی است؛ یک فلگ سروری + بازخوانی در هر استارت لازم است.
3. **رسید Play را سمت سرور اعتبارسنجی کنید** (Purchase API) — نه فقط `acknowledge` سمت کلاینت.
4. **قیمت مرجع تخفیف را ذخیره کنید.** برای انطباق با Omnibus، «کمترین قیمت ۳۰ روز گذشته» را نگه دارید و همان را نشان دهید.
5. **آزمون A/B روی دیوار سطح ۲۰۰۰–۲۵۰۰.** افت حرکت از ۳۲ به ۲۷ در همین بازه رخ میدهد؛ همانجا نقطهٔ بهینهٔ فروش «حرکت اضافه» است.
6. **سقف ویدیو را بر اساس eCPM تنظیم کنید**، نه تعداد ثابت. `times: 5, cd: 5` فعلی برای بازارهای Tier-1 محافظهکارانه است.

---

## ۱۲. بازتولید نتایج

```bash
# ۱) تریاژ عمومی
python3 tools/apk-triage.py jewels-classic-5.3.6.apk \
        --md analysis/triage.md --json analysis/triage.json

# ۲) تحلیل اقتصاد (ابزار جدید)
python3 tools/apk-economy.py jewels-classic-5.3.6.apk \
        --md analysis/economy.md --json analysis/economy.json

# ۳) بررسی Flutter (منتفی شد)
rea identify-flutter-build jewels-classic-5.3.6.apk --json
```

خروجیهای خام: `analysis/triage.md`، `analysis/triage.json`، `analysis/economy.md`، `analysis/economy.json`، `analysis/rea-flutter.json`.

> همهٔ اعداد این گزارش از فایل `jewels-classic-5.3.6.apk` با SHA-256 `659aabcbe6bec0dc2274221409410cc0cb8cd80de4fea33391da62bb2f9f3ded` استخراج شدهاند. فایل APK به دلیل حجم در مخزن قرار نگرفته است.
