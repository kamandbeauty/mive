# پیش‌نمایش طراحی

| نسخه | فایل‌ها | چیست |
|---|---|---|
| ۱٫۷٫۰ | `premium_home_170.png` | خانه: کاراکتر تصویری، پلاک لوگو با تاج، چیپ‌ها، دکمهٔ بازی، داک |
| ۱٫۷٫۰ | `premium_result_170.png` | «آفرین!»: پلاک، چیپ، سوکت‌ها، پلیت امتیاز، کپسول، دکمه‌ها |
| ۱٫۷٫۰ | `art_atlas_170.png` | اطلس همهٔ ۴۰ فایل `assets/art/` |
| ۱٫۷٫۰ | `mock_home_170.py` / `mock_result_170.py` | اسکریپت ترکیب با همان اعداد `HomeLayout` / `VictoryGeometry` و همان نُه‌برش کد |
| ۱٫۶٫۰ | `premium_result.png` | نسخهٔ تمام‌برداری (پیش از تصویری‌شدن)؛ مرجع «قبل» |
| مرجع | `premium_reference.png` | تصویر کاربر (۳۶۰×۵۴۰) |

این‌ها **شبیه‌سازی** هستند، نه اسکرین‌شات دستگاه: هر عدد و هر لایه از کد Kotlin
کپی شده (`Art.nineSlice`، `Art.frame`، `Art.woodBody`، `Art.buttonSkin`،
`Art.socketPlate`، `Art.rewardCapsule`, `HomeLayout`, `VictoryGeometry`)، اما
رندر با Pillow انجام شده است. برای ساختن دوباره:

```bash
cd game-android/design-preview
python3 mock_home_170.py     # → premium_home_170.png
python3 mock_result_170.py   # → premium_result_170.png
python3 ../tools/make-art.py # → assets/art/*  (نیاز به Pillow + numpy)
```

## ۱٫۵٫۰ — نقشهٔ هندسی صفحه‌ها

| فایل | اندازه |
|---|---|
| `ui_home_320/360/412.png` | ۳۲۰×۶۴۰، ۳۶۰×۸۰۰، ۴۱۲×۹۱۵ |
| `ui_game_320/360/412.png` | همان اندازه‌ها |
| `ui_shop_320/360/412.png` | همان اندازه‌ها |
