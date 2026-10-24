# APK triage — `com.dysugar.jewels.prince`

- **File:** `jewels-classic-5.3.6.apk` (61,134,581 bytes, sha256 `659aabcbe6bec0dc…`)
- **App:** Jewels Classic v5.3.6 (537)
- **SDK:** min 21 / target 33
- **Components:** 68 activities, 18 services, 13 receivers, 13 providers
- **Signing:** v1=True v2=True v3=True
- **DEX:** 6 file(s), 54,829 classes, 209,333 strings

## Manifest flags
- `allow_backup` = `true`
- `cleartext_traffic` = `true`
- `network_security_config` = `@7F150005`

## Permissions
32 declared, 2 flagged:
- `android.permission.POST_NOTIFICATIONS` — notifications
- `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` — Play install referrer

## Exported components
- activity: `org.cocos2dx.cpp.AppActivity` — **no permission guard**
- activity: `com.google.firebase.auth.internal.GenericIdpActivity` — **no permission guard**
- activity: `com.google.firebase.auth.internal.RecaptchaActivity` — **no permission guard**
- activity: `com.facebook.CustomTabActivity` — **no permission guard**
- service: `androidx.work.impl.background.systemjob.SystemJobService` (permission: `android.permission.BIND_JOB_SERVICE`)
- service: `com.google.android.gms.auth.api.signin.RevocationBoundService` (permission: `com.google.android.gms.auth.api.signin.permission.REVOCATION_NOTIFICATION`)
- receiver: `com.google.firebase.iid.FirebaseInstanceIdReceiver` (permission: `com.google.android.c2dm.permission.SEND`)
- receiver: `androidx.work.impl.diagnostics.DiagnosticsReceiver` (permission: `android.permission.DUMP`)
- provider: `com.facebook.FacebookContentProvider` — **no permission guard**
- provider: `io.appmetrica.analytics.internal.PreloadInfoContentProvider` — **no permission guard**

## Engines and native code
- ABIs: arm64-v8a, armeabi-v7a (37,124,624 bytes of `.so`)
- Libraries (20): `libEncryptorP.so`, `libEncryptorP.so`, `libMyGame.so`, `libMyGame.so`, `libPglmetasec_ov.so`, `libPglmetasec_ov.so`, `libapminsighta.so`, `libapminsighta.so`, `libapminsightb.so`, `libapminsightb.so`, `libbuffer_pg.so`, `libbuffer_pg.so`, `libc++_shared.so`, `libc++_shared.so`, `libc+.so`, `libc+.so`, `libfile_lock_pg.so`, `libfile_lock_pg.so`, `libmmkv.so`, `libmmkv.so`

## SDK / tracker fingerprints
- Google Play Services (13248 classes)
- Mintegral (2920 classes)
- AppLovin (2563 classes)
- Google Ads / AdMob (981 classes)
- Facebook SDK (859 classes)
- Unity Engine (709 classes)
- IronSource (661 classes)
- Vungle (619 classes)
- AppsFlyer (305 classes)
- Unity Ads (30 classes)

## Capability signals
- **dynamic-code-loading**: `Ldalvik/system/DexClassLoader;`, `Ldalvik/system/PathClassLoader;`, `Ldalvik/system/InMemoryDexClassLoader;`
- **root-check**: `/system/app/Superuser.apk`, `/system/xbin/su`
- **emulator-check**: `goldfish`, `ranchu`, `ro.kernel.qemu`
- **media-codec**: `Landroid/media/MediaCodec`
- **packer/unpack**: `Ljava/util/zip/ZipInputStream;`

## Network endpoints
223 distinct URLs, 15 cleartext.
- http://chartboo.st/publishing
- http://developer.android.com/google/play-services/setup.html.
- http://gateway.adsfall.com:8091
- http://localhost
- http://localhost/
- http://market.android.com/
- http://ow.pubmatic.com/openrtb/2.5
- http://schemas.applovin.com/android/1.0
- http://whatwg.org/C#alphanumeric-ascii-characters
- http://whatwg.org/html/common-microsyntaxes.html#space-character
- http://whatwg.org/html/webappapis.html#dom-windowbase64-atob
- http://whatwg.org/html/webappapis.html#dom-windowbase64-btoa
- http://www.example.com
- http://www.fyber.com
- http://www.google.com
- https://%1$s-%2$s.cloudfunctions.net/%3$s
- https://%s/%s
- https://%s/%s/%s
- https://%sadrevenue.%s/api/v2/generic/v6.12.1/android?app_id=
- https://%sadrevenue.%s/api/v2/log/AdImpression/v6.12.1/android?app_id=
- IPs: 1.0.0.0, 11.9.0.0, 16.4.41.0, 3.6.0.0, 4.6.1.1, 5.2.0.3, 6.12.1.1, 6.16.0.0, 7.1.10.0, 8.1.9.0

## Secret-like strings (review manually)

- `Query[%s,%s,PageToken=%s,Spaces=%s]`
- `gads:cached_token:ttl_millis`
- `otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=%s&digits=%d`

## REA cross-check
- `identify_flutter_build`: flutter_detected=False, ABIs=[]
