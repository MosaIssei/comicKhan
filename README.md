# کمیک‌خوان (ComicKhan)

یک خوانندهٔ کمیک و وب‌تُون فارسی، ساخته‌شده با Kotlin و Jetpack Compose — هم برای اندروید و هم برای ویندوز.

[![Build APK](https://github.com/MosaIssei/comicKhan/actions/workflows/android.yml/badge.svg)](https://github.com/MosaIssei/comicKhan/actions/workflows/android.yml)
[![Build Desktop](https://github.com/MosaIssei/comicKhan/actions/workflows/desktop.yml/badge.svg)](https://github.com/MosaIssei/comicKhan/actions/workflows/desktop.yml)

---

## امکانات

**قالب‌ها**

| قالب | اندروید | ویندوز |
|---|---|---|
| `CBZ` / `ZIP` | ✅ | ✅ |
| `CBR` / `RAR` | ✅ | ✅ |
| `PDF` | ✅ | — |
| `MHT` / `MHTML` | ✅ | ✅ |
| پوشهٔ تصاویر | ✅ | ✅ |

آرشیوهای تودرتو هم پشتیبانی می‌شوند: اگر داخل یک ZIP چند ZIP یا RAR دیگر باشد، صفحه‌هایشان به‌ترتیب پشت سر هم نمایش داده می‌شوند.

> نکته: فایل‌های `RAR5` پشتیبانی نمی‌شوند. اگر خطای RAR5 دیدی، با 7-Zip به ZIP تبدیلش کن.

**حالت مطالعه**

- **صفحه‌به‌صفحه** — با پشتیبانی راست‌به‌چپ (مانگا) و چپ‌به‌راست، و نمایش دو صفحه‌ای
- **پیوسته (وب‌تُون)** — اسکرول عمودی با بزرگ‌نمایی دو انگشتی؛ زوم به‌صورت ترنسفورم رسم اعمال می‌شود، پس ارتفاع صفحه‌ها وسط کار عوض نمی‌شود و لیست نمی‌پرد
- **دیکد ناحیه‌ای** — هنگام زوم فقط بخش دیده‌شده با `BitmapRegionDecoder` و در رزولوشن کامل دیکد می‌شود، پس تصویر تار نمی‌شود و حافظه محدود می‌ماند
- **برش خودکار حاشیه** — حاشیهٔ سفید/تیرهٔ اطراف صفحه پیدا و حذف می‌شود. شدتش از تنظیمات قابل تغییر است (حاشیهٔ افقی و عمودی جدا). برای نوارهای بلند وب‌تُون، شبکهٔ تحلیل بر اساس ضلع کوتاه ساخته می‌شود تا حاشیهٔ کناره‌ها دقیق پیدا شود
- **ادامه از آخرین صفحه** — هم شمارهٔ صفحه و هم فاصلهٔ داخل صفحه ذخیره می‌شود، پس دقیقاً همان‌جایی که رها کردی باز می‌شود
- **بوکمارک** صفحه‌ها و پرش به شمارهٔ صفحه
- روشنایی، کنتراست و معکوس‌کردن رنگ‌ها
- حالت تناسب (کل صفحه / عرض / ارتفاع)، قفل چرخش صفحه
- گروه‌بندی‌شده در یک پنل تنظیمات اسکرول‌شو

**سایر**

- رابط کاربری کاملاً فارسی
- ماندن صفحه روشن در حین مطالعه
- گزارشگر خطای روی‌دستگاه: اگر برنامه کرش کند، دفعهٔ بعد متن خطا را نشان می‌دهد
- باز کردن فایل از بیرون برنامه (Open with) با مدیریت درست دسترسی‌های موقت

---

## دانلود

از تب [**Releases**](https://github.com/MosaIssei/comicKhan/releases):

- **اندروید** — فایل `app-release.apk`
- **ویندوز** — نصب‌کنندهٔ `.msi` یا نسخهٔ پرتابل

---

## ساخت از سورس

نیازمندی‌ها: **JDK 17** و **Gradle 8.9**. (پروژه فعلاً Gradle Wrapper ندارد، پس از گریدل نصب‌شده روی سیستم استفاده کن. اگر ترجیح می‌دهی، با `gradle wrapper --gradle-version 8.9` یکی برایش بساز.)

### اندروید

```bash
gradle :app:assembleDebug
```

خروجی در `app/build/outputs/apk/debug/`.

برای نسخهٔ ریلیز، کلید امضا از متغیرهای محیطی خوانده می‌شود و اگر نباشد به کلید debug برمی‌گردد:

```bash
export KEYSTORE_FILE=/path/to/keystore.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
gradle :app:assembleRelease
```

### ویندوز

```bash
gradle :desktop:run
```

برای ساخت بستهٔ نصبی و نسخهٔ پرتابل:

```bash
gradle :desktop:packageMsi
gradle :desktop:createDistributable
```

خروجی در `desktop/build/compose/binaries/`.

---

## ساختار پروژه

```
comicreader-fa/
├── app/                                   # اپ اندروید
│   └── src/main/java/ir/comicreader/fa/
│       ├── MainActivity.kt                # ورودی، Open-with، صفحهٔ کرش
│       ├── data/                          # منابع کمیک و دیکد تصویر
│       │   ├── ComicSource.kt             # اینترفیس مشترک همهٔ قالب‌ها
│       │   ├── ZipComicSource.kt          # ZIP/CBZ، آرشیو تودرتو
│       │   ├── RarComicSource.kt          # RAR/CBR با junrar
│       │   ├── PdfComicSource.kt          # PDF با PdfRenderer
│       │   ├── MhtmlComicSource.kt        # صفحه‌های وب ذخیره‌شده
│       │   ├── FolderComicSource.kt       # پوشهٔ تصاویر
│       │   ├── ImageDecode.kt             # دیکد با نمونه‌برداری و سقف حافظه
│       │   ├── RegionDecode.kt            # دیکد ناحیه‌ای (BitmapRegionDecoder)
│       │   ├── AutoCrop.kt                # تشخیص و برش حاشیه
│       │   └── Prefs.kt                   # تنظیمات
│       └── ui/
│           ├── library/                   # کتابخانه: شبکه/فهرست، مرتب‌سازی
│           ├── reader/                    # خواننده: نمایش، زوم، تنظیمات
│           └── CrashScreen.kt
├── desktop/                               # نسخهٔ ویندوز (Compose Multiplatform)
│   └── src/main/kotlin/ir/comicreader/fa/desktop/
├── gradle/libs.versions.toml              # نسخهٔ کتابخانه‌ها
└── .github/workflows/                     # CI
```

**پشتهٔ فنی:** Kotlin 2.0 · Jetpack Compose (Material 3) · Compose Multiplatform 1.7 · AGP 8.5 · `minSdk 26`، `targetSdk 35` · junrar 7.5.5 برای RAR

---

## انتشار نسخهٔ جدید

۱. در `gradle.properties` شمارهٔ نسخه را بالا ببر — **`versionCode` حتماً باید بزرگ‌تر شود**، وگرنه اندروید نصب روی نسخهٔ قبلی را رد می‌کند:

```properties
comickhan.versionCode=2
comickhan.versionName=1.1
```

۲. کامیت کن، پوش کن، و یک تگ بزن:

```bash
git tag v1.1
git push origin v1.1
```

با پوش تگ، ورک‌فلوها خودکار APK و MSI را می‌سازند و به یک **GitHub Release** با همان نام تگ ضمیمه می‌کنند.

برای اینکه APK با کلید امضای خودت امضا شود (و نه کلید debug)، این چهار سکرت را در **Settings → Secrets and variables → Actions** تعریف کن:

`KEYSTORE_BASE64` · `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD`

اگر هنوز keystore نداری، از تب Actions ورک‌فلوی **Make release keystore** را دستی اجرا کن؛ هم فایل `.jks` و هم متن base64 آمادهٔ سکرت را به‌صورت artifact تحویل می‌دهد.
