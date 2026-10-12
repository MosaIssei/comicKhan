# کمیک‌خوان (ComicKhan)

یک خوانندهٔ کمیک و وب‌تُون فارسی، برای اندروید و ویندوز.

[![Build APK](https://github.com/MosaIssei/comicKhan/actions/workflows/android.yml/badge.svg)](https://github.com/MosaIssei/comicKhan/actions/workflows/android.yml)
[![Build Desktop](https://github.com/MosaIssei/comicKhan/actions/workflows/desktop.yml/badge.svg)](https://github.com/MosaIssei/comicKhan/actions/workflows/desktop.yml)

## امکانات

- پشتیبانی از `CBZ` / `ZIP`، `CBR` / `RAR`، `PDF` و `MHT` / `MHTML`، و پوشهٔ تصاویر
- آرشیوهای تودرتو — اگر داخل یک آرشیو، آرشیو دیگری باشد، صفحه‌هایشان پشت سر هم نمایش داده می‌شود
- دو حالت مطالعه: **صفحه‌به‌صفحه** و **پیوسته (وب‌تُون)**
- جهت خواندن راست‌به‌چپ (مانگا) و چپ‌به‌راست
- نمایش دو صفحه‌ای
- بزرگ‌نمایی با دو انگشت
- برش خودکار حاشیه‌های اضافه، با شدت قابل تنظیم
- ادامه از آخرین صفحه‌ای که خواندی
- بوکمارک و پرش به شمارهٔ صفحه
- روشنایی، کنتراست و معکوس‌کردن رنگ‌ها
- حالت تناسب صفحه (کل صفحه / عرض / ارتفاع) و قفل چرخش صفحه
- رابط کاربری فارسی

> فایل‌های `RAR5` پشتیبانی نمی‌شوند. اگر با این خطا روبه‌رو شدی، با 7-Zip به ZIP تبدیلش کن.

## دانلود

از تب [**Releases**](https://github.com/MosaIssei/comicKhan/releases):

- **اندروید** — فایل `app-release.apk`
- **ویندوز** — نصب‌کنندهٔ `.msi` یا نسخهٔ پرتابل

## ساخت از سورس

نیاز به **JDK 17** و **Gradle 8.9** داری. پروژه فعلاً Gradle Wrapper ندارد، پس از گریدل نصب‌شده روی سیستم استفاده کن (یا با `gradle wrapper --gradle-version 8.9` برایش بساز).

اندروید:

```bash
gradle :app:assembleDebug
```

ویندوز:

```bash
gradle :desktop:run
```

## انتشار نسخهٔ جدید

۱. در `gradle.properties` شمارهٔ نسخه را بالا ببر — `versionCode` باید بزرگ‌تر شود، وگرنه اندروید نصب روی نسخهٔ قبلی را قبول نمی‌کند:

```properties
comickhan.versionCode=2
comickhan.versionName=1.1
```

۲. کامیت و پوش کن، بعد یک تگ بزن:

```bash
git tag v1.1
git push origin v1.1
```

با پوش تگ، APK و MSI خودکار ساخته و به یک GitHub Release با همان نام ضمیمه می‌شوند.

برای امضای APK با کلید خودت، این چهار سکرت را در **Settings → Secrets and variables → Actions** تعریف کن:

`KEYSTORE_BASE64` · `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD`

اگر هنوز keystore نداری، از تب Actions ورک‌فلوی **Make release keystore** را دستی اجرا کن.
