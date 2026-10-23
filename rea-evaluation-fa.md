# ارزیابی عملی REA — «هر چیزی را مهندسی معکوس کن»

> بررسی میدانی مخزن [morluto/rea](https://github.com/morluto/rea) (نسخهٔ `rea-agents@6.7.0`، مجوز MIT)
> روی یک اپ Electron واقعی که به‌صورت بسته‌بندی‌شده (`.asar`) در اختیار ابزار قرار گرفت.
>
> تاریخ بررسی: ۲۰۲۶-۱۰-۲۳ · محیط: Linux x64 / Node.js 22.22.3 / بدون Ghidra، Hopper، IDA یا JDK

---

## ۰) جواب کوتاه

**بله — برای اپ‌های Electron/JavaScript همین حالا و بدون هیچ ابزار جانبی کار می‌کند و خیلی هم خوب کار می‌کند.**
در چند ثانیه از یک بستهٔ ۳۴ کیلوبایتی، نقشهٔ کامل IPC، سطح `contextBridge`، آپدیتر، لایسنس، اندپوینت‌های شبکه،
source map و حتی یک افزونهٔ بومی خارج از بسته را بیرون کشید؛ بعد با Wakaru باندل مینیفای‌شده را به ۶ ماژول
خوانا برگرداند و در مقایسه با سورس اصلی، فایل دست‌کاری‌شده را با دقت `high` به‌عنوان «modified» علامت زد.

**برای بقیهٔ اپ‌ها:** خود REA موتور تحلیل نمی‌آورد؛ Ghidra/Hopper/IDA/JADX/Frida/tshark/apktool را
از شما می‌خواهد. یعنی «ابزار همه‌کاره» نیست، «لایهٔ هماهنگ‌کننده + شواهد» است — و در همین نقش، طراحی‌اش
به‌طور غیرعادی دقیق و صادقانه است.

| هدف | وضعیت در این بررسی | پیش‌نیاز |
|---|---|---|
| Electron / JavaScript / ASAR | ✅ تست شد، عالی | فقط Node + `ps` |
| بازیابی سورس از باندل مینیفای | ✅ تست شد (با Wakaru) | باینری Wakaru (از npm قابل نصب) |
| مقایسهٔ سورس با باندل / تشخیص دست‌کاری | ✅ تست شد | — |
| مقایسهٔ دو نسخهٔ اپ | ⚠️ تست شد، بسیار محافظه‌کار | — |
| باینری بومی (C/C++/Rust/Go/…)، دیکامپایل | ❌ تست نشد (موتور نصب نبود) | Ghidra 12.1.x / Hopper / IDA |
| APK اندروید، دیکامپایل کلاس‌ها | ❌ تست نشد (JDK نبود) | JDK کامل + `jadx-headless-mcp` |
| APK: Flutter/Dart AOT | ✅ ابزار CLI تست شد (روی یک APK نمونه) | بدون JDK |
| APK: امضا | ⚠️ `inspect_apk_signature` در ۶.۷.۰ وجود ندارد؛ با ابزار بیرونی (androguard) انجام شد | — |
| APK: manifest و دیکامپایل کلاس | 📄 فقط مستندات | JDK 17+ و JADX |
| IPA / macOS / Frida / ADB / pcap | 📄 فقط مستندات | macOS، دستگاه، tshark |

---

## ۱) در این بررسی دقیقاً چه کردم

1. نصب از npm: `npm install -g rea-agents` → نسخهٔ `6.7.0` (بدون خطا).
2. `rea doctor` و `rea capabilities` برای دیدن وضعیت واقعی محیط.
3. ساخت یک اپ Electron نمونه و واقع‌نما («Nimbus Notes») با:
   لایسنس HMAC، کانال IPC برای فعال‌سازی آفلاین، سینک ابری، آپدیتر، store روی دیسک،
   preload با `contextBridge`، و یک افزونهٔ بومی `better_sqlite3.node` که در `app.asar.unpacked` قرار می‌گیرد.
   باندل با esbuild مینیفای شد؛ برای renderer عمداً `sourceMap` روشن ماند (مثل خیلی از اپ‌های واقعی).
4. بسته‌بندی با `@electron/asar` و تحلیل همان فایل نهایی — بدون دسترسی ابزار به سورس.
5. اجرای زنجیرهٔ کامل: تحلیل → نماها → ترِیس کانال → ترِیس معنایی راز → بازیابی سورس →
   مقایسهٔ سورس/باندل → مقایسهٔ دو نسخه → آزمون سرور MCP.
6. سورس این اپ نمونه برای بازتولید در [`examples/rea-demo-app/`](examples/rea-demo-app/) همراه این گزارش است
   (بدون `node_modules`؛ با `node build.mjs && node package.mjs` همان بسته ساخته می‌شود).
7. ساخت نسخهٔ ۳.۳.۰ با تغییرات واقعی (افزودن `ai:summarize`، حذف درپشتی `license:offline`،
   چرخش کلید HMAC، تغییر API base) و تکرار تحلیل برای مقایسه.

---

## ۲) REA در یک نگاه

- **دو رابط روی یک موتور:** CLI با ~۱۵۵ دستور و سرور MCP با **۲۰۷ ابزار** (اندازه‌گیری‌شده با `tools/list` روی `rea --mcp`).
  پروتکل MCP: `2025-06-18`؛ نام سرور: `rea` نسخهٔ `6.7.0`.
- **مدل «Evidence»:** هر تحلیل یک رکورد JSON می‌دهد که با هش محتوا و شناسهٔ والد احراز هویت می‌شود و شامل `authority`
  (`artifact-bytes` / `ast-static-analysis` / `static-relationship-inference` / `analyst-inference`)،
  `state` (`observed` / `inferred` / `unknown` / `unavailable`) و `confidence` است.
  نتیجه: هیچ‌چیز «حدسِ بی‌برچسب» نیست؛ هر ادعا می‌گوید از کجا آمده و چقدر مطمئن است.
- **Providerها:** بخشی درون‌ساخت (JS/Electron، .NET، Go، Rust، ELF/PE، WASM، IL2CPP، Godot، PyInstaller،
  Flutter metadata، SQLite، EVM، Erlang BEAM، …) و بخشی «Caller-supplied» (Ghidra/Hopper/IDA/JADX/Frida/apktool/tshark/sigrok/objdump/rizin/Wakaru).
  REA هیچ‌کدام را **نصب یا آپدیت نمی‌کند** (تنها استثنا: `setup` می‌تواند Hopper را با تأیید شما نصب کند) — از منظر زنجیرهٔ تأمین خوب، از منظر «تازه‌کار» کمی دردسر.
- **اجرای محلی:** تحلیل استاتیک است و کد اپ اجرا نمی‌شود؛ گردش‌کارهای runtime (مرورگر/Electron/process/ADB)
  جدا و با اثرات مشخص (`launches_process`, `may_access_network`, …) تعریف شده‌اند.

خروجی `rea doctor` در این محیط:

```
healthy: false
node 22.22.3  ok
host debian 12 ok
hopper            missing_analysis_engine
ghidra            GHIDRA_INSTALL_DIR is not set
ida-registration  not configured
jadx              REA_JADX_MCP_JAR is not set
skill:identity    config_drift (setup اجرا نشده)
```

یعنی ابزار سالم نصب می‌شود ولی تا وقتی موتور/پلاگین ندهی، تحلیل بومی و اندروید در دسترس نیست.
(نسخهٔ رسمی Node را `^22.23.4` می‌خواهد؛ اینجا `22.22.3` بود و بدون مشکل کار کرد — ولی برای پروژهٔ واقعی همان نسخهٔ اعلامی را بگذارید.)

---

## ۳) اپ هدف و اعداد تحلیل

| مورد | مقدار |
|---|---|
| فایل | `app.asar` (۳۳٬۹۹۲ بایت) + `app.asar.unpacked` |
| زمان تحلیل | **۳٫۷ ثانیه** (`rea analyze-javascript-application`) |
| حجم Evidence | **۴٬۹۶۰٬۱۹۳ بایت** (~۵ مگابایت؛ ≈۱۴۵ برابر خودِ بسته) |
| فایل‌های مرتبط / JS پارس‌شده | ۷ / ۳ |
| گره‌های AST بازدیدشده | ۳٬۸۷۵ |
| یافته‌ها | ۶۰ |
| گراف ساختاری | ۱۰۶ گره، ۱۵۲ یال |
| گراف معنایی | ۲٬۷۰۸ گره، ۲٬۴۷۵ رابطه، ۹۰۳ «unknown»، ۱۱۹ اثرانگشت تابع |
| پوشش | `application: partial` / `semantic: unknown` (صادقانه اعلام می‌شود) |

نمای خلاصه (`inspect-analysis-view` با `view.kind = summary`) همان Evidence را در **۱۲ کیلوبایت** خلاصه می‌کند —
روش درست برای اینکه یک ایجنت ۵ مگابایت را داخل context نریزد.

خلاصهٔ سطح Electron که ابزار خودش تولید کرد:

```
browser_windows: 1            preload_entrypoints: 1
context_bridge_apis: 1        exposed_api_members: 5
ipc.operations: 27            ipc.literal_channels: 13
ipc.main_handlers: 13         ipc.paired_renderer_transmissions: 13
ipc.ambiguous: 0              ipc.unpaired_literal: 0
ipc.dynamic_channel_operations: 1
sender_validation_observations: 0
native_addon_bindings: 0      resolved: 0
```

نکته‌های ریز اما مهم:

- کانال داینامیک `e` در preload (`ipcRenderer.on(channel, …)` داخل wrapper allow-list) به‌درستی
  **جفت نشد** و به‌عنوان unresolved ماند؛ REA اسم‌ها را بر اساس شباهت متنی حدس نمی‌زند.
- `contextBridge` با `resolution_basis: wrapper-substitution` و ۵ عضو (`app, license, notes, on, sync`)
  با اطمینان `high` بازسازی شد.
- `electron-main` از `package.json:main` و `electron-preload` از property `preload` با `high` حل شد.
- `source-map`: `declared_url: renderer.js.map` → `resolved: dist/renderer.js.map` و یک گرهٔ
  `source-module` با مسیر اصلی `../src/renderer/index.js` ساخته شد.
- افزونهٔ بومی **بدون هیچ `require` در JS** هم پیدا شد: `native-addon` با sha256 و `unpacked: true`
  و یک `native handoff` با وضعیت `requires-provider-analysis` و ابزارهای پیشنهادی
  (`open_binary`, `binary_overview`, `search_procedures`, `analyze_function`, `xrefs`).

---

## ۴) ترِیس ویژگی (Feature tracing)

### ۴.۱ ترِیس یک کانال IPC

```bash
rea trace-application-feature trace-channel.json --json
# seed: { kind: "channel", value: "license:offline", match: "exact" }, direction: "both"
```

نتیجه: ۱ تطابق، **۱۰۶ گره / ۱۵۲ یال / ۱۴ مسیر پایانی / ۱ handoff بومی**،
۹۶ واقعیت `observed` و ۱۸۵ واقعیت `inferred`.

درس عملی: ترِیس روی *گراف ساختاری* از کانال، از طریق ماژول مشترک (`preload.js`)
به کل سطح API پخش می‌شود؛ مسیرها درست‌اند ولی کلی. برای دیدن
«API → کانال → ترمینال» دقیق، گراف معنایی گرهٔ `boundary` را دارد:

```
boundary | nimbus.license.offline
  entry_path: ["nimbus","license","offline"]
  channel:    "license:offline"
  terminal:   "electron.ipcRenderer.invoke"
  step_kind:  "exposure"     resolution: "candidate"
```

### ۴.۲ ترِیس معنایی یک راز هاردکدشده

```bash
rea trace-javascript-semantics sem-secret.json --json
# query: { seed: { kind: "literal", value: "nimbus-demo-license-key-2024" }, direction: "forward-influence" }
```

نتیجه: `status: ambiguous`، ۱ تطابق seed، ۵ گره، ۴ رابطه، **۳ unknown**، پوشش `partial`.
زنجیرهٔ بازیابی‌شده: `literal → binding z → argument 1 → function A (createHmac)`.

این یعنی ابزار نه‌فقط رشته را پیدا می‌کند، بلکه می‌گوید **کجا مصرف می‌شود** — و در همان حال
صادقانه می‌گوید «جریان داده کامل نیست» (۳ ناشناخته) تا کسی خیال نکند همه‌چیز اثبات شده.

> نکتهٔ ریز: روی `trace-application-feature` با `seed.kind: "string"` همین مقدار **هیچ تطابقی نداشت**،
> در حالی که در گراف معنایی به‌عنوان `literal` وجود داشت. ابزار درست برای داده‌های معنایی،
> `trace-javascript-semantics` است. (ترِیس ساختاری، literalها را نود نمی‌کند.)

---

## ۵) بازیابی سورس: «مینیفای‌شده» → کد خوانا

`recover-javascript-sources` به‌تنهایی کار نمی‌کند و می‌گوید:

```
capability_unavailable — Provide an absolute REA_WAKARU_COMMAND pointing to a Wakaru ^1.13.0 Linux x64 binary
```

با نصب `@wakaru/cli@1.14.0` از npm و دادن مسیر مطلق باینری پلتفرم:

```bash
export REA_WAKARU_COMMAND=/usr/local/lib/node_modules/@wakaru/cli/node_modules/@wakaru/cli-linux-x64/wakaru
rea recover-javascript-sources /tmp/target/dist/main.js /tmp/target/recovered \
    --extraction-mode structural --rewrite-level standard --json
```

نتیجه: `detected_formats: ["esbuild"]`, `safety: normal`، **۶ ماژول** (`entry`, `_`, `F`, `O`, `R`, `g`)،
۰ خطا، و خروجی همراه با source map و provenance. نمونهٔ خروجی (بازسازی‌شده از یک خط مینیفای):

```js
import { app, BrowserWindow, ipcMain, dialog, shell, Menu } from "electron";
import w from "node:path";
const { NoteStore } = F();
const { evaluateLicense, encodeLicense, machineFingerprint } = _();
const S = w.join(app.getPath("userData"), "license.dat");

function M() {
    i = new BrowserWindow({
        width: 1180, height: 760,
        webPreferences: {
            preload: w.join(__dirname, "preload.js"),
            contextIsolation: true,
            nodeIntegration: false,
            sandbox: true,
            spellcheck: true
        }
    });
    ...
```

یعنی نام‌های محلی (مثل `i`, `w`, `M`) باقی می‌مانند، اما ساختار، importها، ثابت‌ها و منطق کاملاً خوانا می‌شود.
ادعا نمی‌کند که خروجی با رفتار runtime معادل است — و همین ادعانکردن، درست است.

---

## ۶) مقایسهٔ سورس اصلی با باندل (تشخیص دست‌کاری)

```bash
rea import-reference-source <root-of-source-tree> --json > ref.json
rea compare-source-to-bundle s2b.json --json     # { reference, application }
```

| سناریو | نتیجه |
|---|---|
| ریشه = `src/` | `renderer/index.js` → `unchanged / exact` (score 125: exact-source-digest) و ۶ فایل `unknown` |
| ریشه = ریشهٔ پروژه (شامل `src/`) | `src/renderer/index.js` → `unchanged / exact` (score **195**: exact-source-digest + **source-map-original-path**) و `src/renderer/index.html` → `unchanged / exact` |
| همان ریشه، اما یک کاراکتر در سورس عوض‌شده | `src/renderer/index.js` → **`modified / high`** (score 95: source-map-original-path) |

این دقیقاً همان کاری است که در ممیزی زنجیرهٔ تأمین می‌خواهید:
«آیا این باندل از همان سورسی ساخته شده که در اختیار دارم؟ کدام فایل دست خورده؟»
و درس عملی: **ریشهٔ reference را همان ریشهٔ پروژه بدهید**، نه پوشهٔ `src` — وگرنه سیگنال
`source-map-original-path` (وزن ۷۰) از دست می‌رود و فقط تطابق بایت‌به‌بایت می‌ماند.

---

## ۷) مقایسهٔ دو نسخه (۳.۲.۱ در برابر ۳.۳.۰)

`rea compare-application-versions`:

```
unchanged 0 | added 0 | removed 0 | changed 15 | unknown 167
matching: exact 0, source-map-identity 1, semantic-key 30, unmatched 151
```

این «بد» نیست، **محافظه‌کار** است: چون هر دو باندل بازسازی و مینیفای شده‌اند و هویت پایدار
(ordinal ماژول، نام) وجود ندارد، ابزار ترجیح می‌دهد «نمی‌دانم» بگوید تا اینکه جفت‌سازی حدسی کند.
برای «چه چیزی در سطح تماس تغییر کرده» ابزار درست، خودِ Evidence است؛ با یک اسکریپت کوچک
(`tools/rea-surface-diff.mjs` در همین مخزن) خروجی دقیق می‌شود:

```text
IPC channels: 26 -> 26
  + main:handle       ai:summarize    (dist/main.js)
  + renderer:invoke   ai:summarize    (dist/preload.js)
  - main:handle       license:offline (dist/main.js)
  - renderer:invoke   license:offline (dist/preload.js)
Endpoints:
  + https://api.nimbus-notes.example/v2
  - https://api.nimbus-notes.example/v1
Long literals (possible keys/flags):
  + nimbus-demo-license-key-2025
  - nimbus-demo-license-key-2024
```

این ترکیب (Evidence ساختاریافته + پس‌پردازش کوچک) در عمل از «compare» خام کاربردی‌تر است.

---

## ۸) یافته‌های امنیتی روی اپ نمونه

اینها یافته‌های اپ نمونه‌اند (که عمداً مثل یک اپ کوچک واقعی نوشته شده)، نه ایراد REA؛
هدف نشان‌دادن این است که یک پاس تحلیل چه چیزی بیرون می‌دهد.

| # | یافته | چطور پیدا شد | اثر |
|---|---|---|---|
| ۱ | راز HMAC لایسنس داخل باندل (`nimbus-demo-license-key-2024`) | گرهٔ `literal` + ترِیس معنایی | هر کسی می‌تواند لایسنس Pro جعل کند؛ چرخش کلید در ۳.۳.۰ فقط تا نسخهٔ بعدی جواب می‌دهد |
| ۲ | کانال IPC `license:offline` که بدون سرور لایسنس می‌سازد | گراف کانال‌ها + `boundary` | فعال‌سازی رایگان Pro؛ در ۳.۳.۰ حذف شد (diff تأیید می‌کند) |
| ۳ | توکن Bearer = اثرانگشت ماشین (`status.machine`، ۱۶ کاراکتر از hostname+username) | گراف معنایی + خواندن سینک | احراز هویت قابل حدس/تولید مجدد؛ توکن نشستی واقعی نیست |
| ۴ | هیچ اعتبارسنجی sender در ۱۳ handler | `sender_validation_observations: 0` | اگر renderer آلوده شود، همهٔ امکانات main در دسترس است |
| ۵ | آپدیتر به JSON ریموت اعتماد می‌کند (بدون امضا) و `url` را به renderer می‌فرستد | گراف + نود `updater` | در صورت تسخیر فید، مسیر به‌روزرسانی مخدوش می‌شود |
| ۶ | `CSP: style-src 'unsafe-inline'` | AST فایل HTML | سطح حملهٔ XSS بازتر می‌شود؛ قابل حذف است |
| ۷ | `better_sqlite3.node` در `app.asar.unpacked` بدون ارجاع در JS | نود `native-addon` با `unpacked: true` | سطح حمله/باقی‌ماندهٔ بسته‌بندی؛ باید توضیح داده شود |

نکتهٔ خوب اپ: `contextIsolation: true`, `nodeIntegration: false`, `sandbox: true`.
اما REA برای این مقادیر در باندل مینیفای‌شده `status: dynamic` با `expression: "!0"/"!1"` گزارش کرد —
یعنی **مینیفای، اعتماد را کم می‌کند** و عدد `dynamic` را نباید با «ناشناخته و خطرناک» اشتباه گرفت.
همین‌جا یکی از محدودیت‌های واقعی ابزار هم دیده می‌شود.

---

## ۹) نقاط قوت واقعی (چیزهایی که در عمل دیدم)

1. **مدل شواهد** جدی است: هر واقعیت `authority/state/confidence/location` دارد و «نمی‌دانم» را
   با «نبود» اشتباه نمی‌گیرد. این دقیقاً چیزی است که یک ایجنت LLM برای تحلیل قابل‌اعتماد لازم دارد.
2. **دقت در جزئیات Electron:** جفت‌سازی ۱۳/۱۳ کانال، تشخیص کانال داینامیک، بازسازی `contextBridge`
   با `wrapper-substitution`، حل main/preload از `package.json`، و پیدا کردن افزونهٔ بومی unpacked
   که هیچ‌جا require نشده.
3. **پوشش عرضی خیلی زیاد:** ۲۰۷ ابزار MCP از ELF/PE، Go/Rust، IL2CPP، Godot، PyInstaller، WASM،
   SQLite، EVM، BEAM تا pcap، ADB، Frida، مرورگر و آپدیت‌های Electron.
4. **زنجیرهٔ «تحلیل → ترِیس → بازیابی → مقایسه» کامل است** و همه‌چیز JSON قابل‌اسکریپت می‌دهد.
5. **صرفه‌جویی:** تحلیل ۳٫۷ ثانیه‌ای روی بستهٔ کوچک؛ بدون اجرای کد هدف؛ بدون تماس شبکه.
6. **مهندسی حرفه‌ای:** lint/type/test، schema برای ابزارها، `doctor`، shell completion، محدودیت‌های مستند،
   نصب‌نکردن خودکار موتورهای شخص ثالث، پیام‌های خطای ساختاریافته با `remediation`.

---

## ۱۰) محدودیت‌ها و نکات عملی (تجربه‌شده)

1. **پیش‌فرض خالی است، نه آماده.** بدون Ghidra/Hopper/IDA، تحلیل بومی وجود ندارد؛ `doctor` هم
   `healthy: false` می‌دهد. برای اندروید JDK + JADX لازم است (این سندباکس جاوا نداشت).
2. **Wakaru را خودت باید بیاوری.** برای «کد خوانا» باید `REA_WAKARU_COMMAND` را به مسیر مطلق باینری بدهی
   (من از `@wakaru/cli` npm استفاده کردم). REA عمداً نصب نمی‌کند.
3. **CLI بی‌حالت است.** `retained-evidence` فقط داخل یک نشست MCP معنا دارد؛ در ترمینال باید Evidence را
   کامل inline بدهی. پیام خطایش هم دقیقاً همین را می‌گوید.
4. **ناسازگاری مستندات/پیاده‌سازی در ۶.۷.۰:** (الف) `inspect-analysis-view` ورودی
   `source.kind: "evidence-file"` را رد کرد و فقط `inline` / `retained-evidence` را پذیرفت،
   در حالی که `docs/cli.md` آن را توضیح داده است؛ (ب) `inspect_apk_signature` که README
   برای APK معرفی می‌کند، نه در CLI هست و نه در فهرست ۲۰۷ ابزار MCP (با `tools/list` بررسی شد).
5. **دو ترِیس، دو قرارداد:** `trace-application-feature` با `seed.kind: "string"` literalهای گراف معنایی را
   پیدا نمی‌کند؛ `api` هم `entry_path` می‌خواهد. برای دادهٔ معنایی از `trace-javascript-semantics` استفاده کنید.
6. **مقایسهٔ نسخه‌ها بیش از حد محافظه‌کار است** (۰ added/۰ removed برای تغییرات واقعیِ کانال).
   برای diff عملی، پس‌پردازش Evidence لازم است (نمونه‌اش در `tools/`).
7. **حجم Evidence** برای اپ کوچک ۵ مگابایت بود؛ برای اپ‌های واقعی مستندات صدها مگابایت را ذکر می‌کند.
   بدون `summary`/`page`/`module` view، context ایجنت را می‌سوزاند.
8. **تحلیل runtime تست نشد:** مرورگر Chrome-family در این محیط نبود؛ پس `capture-browser-scenario`
   و `analyze-web-bundle` دست‌نخورده ماندند.
9. **عبارت‌های مینیفای‌شده** می‌توانند مقدارهای ثابت را `dynamic` نشان دهند (مثل `!0`).
   این محدودیت ذاتی تحلیل استاتیک است، نه باگ — ولی در گزارش‌نویسی باید دستی تفکیک شود.
10. **Node:** نسخهٔ اعلامی `^22.23.4` است؛ محیط اینجا `22.22.3` بود و کار کرد، اما برای CI نسخهٔ درست را بگذارید.
11. **`ps` سازگار با procps** روی لینوکس برای بعضی providerها لازم است (اینجا موجود بود).

---

## ۱۱) دستور کار برای اپ خودت

### الف) اگر بستهٔ Electron/JS داری (مثل خیلی از اپ‌های دسکتاپ)

```bash
npm i -g rea-agents
rea analyze-javascript-application /path/to/app.asar --json > app.json
jq -c '{source:{kind:"inline",evidence:.},view:{kind:"summary"}}' app.json > view.json
rea inspect-analysis-view view.json
```

### ب) اگر APK اندروید داری (پروژهٔ Flutter مثل همین مخزن)

بدون JDK (اطلاعات ساخت/امضا/Flutter — طبق مستندات؛ در اینجا تست نشد):

```bash
rea identify-flutter-build   app-release.apk
rea inspect-dart-aot         app-release.apk --abi arm64-v8a
```

برای امضا، manifest، مجوزها و اثرانگشت موتورها در همین محیط (بدون JDK) یک تریاژ آفلاین هم نوشته‌ام:
`tools/apk-triage.py` (روی androguard؛ apkid و `rea identify-flutter-build` را هم صدا می‌زند).

با JDK 17+ و `jadx-headless-mcp` (manifest، کلاس و متد):

```bash
export REA_JADX_MCP_JAR=/abs/path/jadx-headless-mcp-0.7.1.jar
rea inspect-android-package   app-release.apk
rea inspect-android-class     app-release.apk com.studiojavid.puzzle.MainActivity
rea search-android-classes    app-release.apk "score"
rea inspect-android-method    app-release.apk com.studiojavid.puzzle.MainActivity onCreate
rea trace-android-references  app-release.apk com.studiojavid.puzzle.MainActivity
```

> در این سندباکس جاوا نصب نبود، پس این دسته **تست نشد** و صرفاً از مستندات رسمی نقل شده است.

### پ) برای پروژهٔ Flutter همین مخزن (کامند)

منطق بازی در Dart است و بعد از `flutter build` به‌صورت AOT داخل `libapp.so` می‌نشیند؛
پس انتظار «برگرداندن Dart به سورس» نداشته باش. کاری که REA واقعاً می‌دهد:

```bash
flutter build apk --release --split-per-abi
APK=build/app/outputs/flutter-apk/app-arm64-v8a-release.apk

rea identify-flutter-build "$APK"        # هش Dart snapshot + build-id موتور -> نگاشت به SDK
rea inspect-dart-aot "$APK" --abi arm64-v8a
#   بخش‌های snapshot، هدرها و string pool:
#   package_uris / dart_uris / class_like_tokens  (نشانی‌ها، مسیر assetها، نام‌های کلاس‌مانند)
rea inspect-android-package "$APK"       # با JDK+JADX: manifest، permissionها، نسخه‌ها
```

- **برای تریاژ عالی است:** قبل از هر کار سنگین می‌فهمی با کدام build از Dart/موتور طرفی
  و داخل payload چه رشته‌هایی هست.
- **برای منطق بازی کافی نیست:** تحلیل `libapp.so` در سطح بومی کار سختی است و REA هم
  ادعای دیکامپایل Dart ندارد. اگر منبع را داری، مسیر درست‌تر «مقایسهٔ باینری با منبع»
  و بررسی یکپارچگی بیلد است، نه مهندسی معکوس AOT.
- برای assetها (`assets/fruit/*.png`، JSONهای سطح) می‌توانی از `inspect-artifact` / `extract-artifact`
  و برای دیتابیس‌های SQLite همراه اپ از `inspect-sqlite-database` استفاده کنی.

### ت) اتصال به ایجنت (روش اصلی مصرف)

```bash
npx rea-agents setup     # ثبت MCP + نصب skill راهنما (با backup تنظیمات)
rea --mcp                # سرور stdio: ۲۰۷ ابزار
```

بعد کافی است به ایجنت بگویی: «با REA بفهم فلان قابلیت چطور کار می‌کند، شواهدش را نشان بده و
برای پروژهٔ من مشابهش را بساز.» — همان چیزی که صفحهٔ اصلی پروژه تبلیغ می‌کند و در این تست،
بخش JS/Electron آن واقعاً محقق شد.

---

## ۱۲) ملاحظات قانونی و اخلاقی

- REA برای مهندسی معکوس **نرم‌افزاری که حق بررسی آن را دارید** ساخته شده (اپ خودت، ممیزی،
  interoperability، پژوهش). روی اپ‌های دیگران، قوانین کپی‌رایت/قرارداد کاربری و قوانین محلی
  (مثل DMCA §1201 در آمریکا) را رعایت کن.
- گردش‌کارهای runtime، فرایند اجرا می‌کنند و می‌توانند فایل بنویسند؛ روی سیستم حساس/مشتری
  با VM جدا و اسنپ‌شات اجرا کن.
- مدل «Evidence» به‌خوبی برای گزارش حقوقی/فنی قابل استناد است، چون منشأ هر ادعا را نگه می‌دارد.

---

## ۱۳) حکم نهایی

| معیار | امتیاز |
|---|---|
| کیفیت تحلیل Electron/JS | ۹/۱۰ |
| دقت و صداقت شواهد | ۹٫۵/۱۰ |
| کامل‌بودن زنجیرهٔ گردش‌کار | ۸/۱۰ |
| آماده‌به‌کار بودن «out of the box» برای اپ بومی/اندروید | ۵/۱۰ (وابسته به موتور جانبی) |
| تجربهٔ CLI/مستندات | ۸/۱۰ (چند ناسازگاری ریز) |
| **جمع‌بندی برای این پرسش** | **۸٫۵/۱۰ — بله، جدی بگیرش** |

اگر هدفت **اپ‌های Electron/JS** است: امروز یکی از بهترین گزینه‌های متن‌باز است؛ مدل شواهدش
برای کار با ایجنت LLM طراحی شده و همین را هم در عمل تحویل می‌دهد.
اگر هدفت **باینری بومی یا APK** است: قبل از هر چیز Ghidra (رایگان) یا JDK+JADX را آماده کن؛
آن‌وقت REA تبدیل می‌شود به همان «یک MCP برای همه‌چیز» که ادعا می‌کند.

---

## پیوست: فرمان‌های اجراشده و خروجی‌ها

```bash
npm install -g rea-agents                       # 6.7.0
rea doctor
rea capabilities
rea analyze-javascript-application app.asar --json > asar-evidence.json      # 3.7s
rea inspect-analysis-view summary.json                                       # 12 KB
rea trace-application-feature trace-channel.json --json                      # 1 seed / 106 nodes
rea trace-javascript-semantics sem-secret.json --json                        # ambiguous / 3 unknowns
rea recover-javascript-sources dist/main.js recovered --extraction-mode structural
rea import-reference-source . --json
rea compare-source-to-bundle s2b.json --json                                 # modified/high
rea compare-application-versions compare-versions.json --json                # 15 changed / 167 unknown
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}' '{"jsonrpc":"2.0","method":"notifications/initialized"}' '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | rea --mcp   # 207 tools
node tools/rea-surface-diff.mjs v1.json v2.json                              # diff دقیق سطح تماس
```

فایل‌های خروجی این بررسی در سندباکس (خارج از مخزن) باقی ماندند: `asar-evidence.json` (~۵MB)،
`summary.json`، `trace-*.json`، `compare-out.json`، `s2b-*.json`، `recovered/`.

---

## English abstract (for sharing)

- Evaluated `rea-agents@6.7.0` (MIT) hands-on against a packaged Electron target, with no source access and no native analysis engine installed.
- Static JavaScript/Electron reconstruction is fast (~3.7 s) and precise: 13 literal IPC channels paired 13/13, one unresolved dynamic channel, a reconstructed `contextBridge` surface, a resolved source map, and an unpacked native addon discovered without any `require`.
- Evidence is explicit about authority, state and confidence ("unknown" is never reported as absence), which makes it well suited for LLM agents (207 MCP tools, protocol `2025-06-18`).
- Recovery of readable modules worked via Wakaru (caller-supplied, `REA_WAKARU_COMMAND`); source-to-bundle comparison correctly flagged a one-character edit as `modified / high`.
- Native binaries and Android class decompilation were **not** tested: they require Ghidra/Hopper/IDA and a JDK + JADX, none of which were available here.
- Verdict: excellent for Electron/JS today; a strong orchestration + evidence layer for everything else once the external engines are supplied.
