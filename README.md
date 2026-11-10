# Odak — Scroll'dan Çalışmaya: Reels, Shorts ve TikTok engelleyici odaklanma uygulaması

> Instagram Reels / YouTube Shorts / TikTok dürtüsünü kısa bir kart çalışmasına çeviren Android dijital detoks uygulaması.

🌐 **Proje sayfası:** [https://enoli13.github.io/odak/](https://enoli13.github.io/odak/) · ⬇ [Son sürümü indir](https://github.com/Enoli13/odak/releases/latest)


Reels/Shorts/TikTok açma dürtüsünü 3 kartlık kısa bir kendini test etme turuna çeviren Android uygulaması.
Tasarım belgesindeki MVP'nin dört modülü + ana döngünün "Ne yapacaktın?" hatırlatıcısı ve hak edilmiş mola.

## İçinde ne var

| Modül | Durum | Nerede |
|---|---|---|
| 1. Başlangıç sözleşmesi (hedef uygulamalar, tek hedef, iki eğer–o zaman planı, her plan iki kez) | ✅ | `ui/onboarding/` |
| 2. Durdurma ekranı (5 sn nefes → büyük "Vazgeç → 3 kart", küçük "Yine de aç", uyarı metni yok) | ✅ | `ui/stop/StopActivity.kt` |
| 3. Kart motoru (önce cevap yaz, sonra çevir; bilinen çıkar, bilinmeyen sona; karışık desteler; "Neden?" kartları) | ✅ | `ui/cards/`, `data/Scheduler.kt` |
| 4. Ritim takvimi (10 haftalık tik tablosu, pazar SRBAI 4 soru, seri sıfırlanmaz, "ara uzadı" notu) | ✅ | `ui/rhythm/`, `work/ReminderWorker.kt` |
| "Az önce ne yapacaktın?" (Yine de aç → uygulamadan çıkınca bildirim) | ✅ | `service/GateService.kt` |
| Hak edilmiş mola (25 dk blok → 10 dk serbest) | ✅ | Ana sayfa + durdurma ekranı |
| Sürekli kaydırma sayacı (10/15/20 dk → "Bırak → 3 kart" / "5 dk daha") | ✅ | `service/GateService.kt`, `GateState.kt` |
| "Şu an ne hissediyorsun?" sorusu ve hisse göre öneri | ✅ | `ui/stop/StopActivity.kt` |
| Toplu bildirim modu (günde 3 özet) | ✅ | `service/BatchListener.kt`, `work/BatchWorker.kt` |
| Kademeli geri çekilme ve bakım modu (SRBAI'ye göre) | ✅ | `data/Repo.kt` → `withdraw()` |
| İlerleme odaklı ana ekran, ekran süresi yalnızca Rapor'da | ✅ | `ui/home/`, `ui/report/` |
| İlk 7 gün ölçüm, 10 haftalık deney raporu, CSV | ✅ | `data/Report.kt`, `ui/report/ReportScreen.kt` |
| Sınav planlayıcı, gri ekran, limitler, akşam kartı | sonra | — |
| PDF'den kart, destekçi modu | v3 | — |

Ek: desteye **toplu kart yapıştırma** (`soru | cevap | neden?` her satıra bir kart) ve isteğe bağlı küçük bir örnek deste.

## APK alma

### Yol 1 — GitHub Releases
1. Bu klasörü yeni bir GitHub deposuna yükle.
2. **Actions** sekmesinde "APK derle" iş akışı kendiliğinden çalışır.
3. main dalına her gönderimde APK derlenir ve `app/build.gradle.kts` içindeki `versionName` ile (ör. `v0.1.0`) Releases sayfasına otomatik yayınlanır. Yeni sürüm için `versionName`i artır.

### Yol 2 — Android Studio
`Odak` klasörünü aç → Gradle senkronize olsun → ▶ Run.
Komut satırından: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

## İlk kurulumdan sonra
1. Uygulamayı aç, başlangıç sözleşmesini doldur.
2. **Erişilebilirlik izni:** Ayarlar → Erişilebilirlik → Odak → aç.
   Android 13+ telefonda APK'dan kurulan uygulamada izin gri görünürse:
   Ayarlar → Uygulamalar → Odak → sağ üstte ⋮ → **Kısıtlanmış ayarlara izin ver**, sonra tekrar dene.
3. Bildirim iznini ver (plan saati ve pazar soruları için).

## Teknik notlar
- Kotlin + Jetpack Compose, minSdk 26, targetSdk 35. Bağımlılıklar yalnızca AndroidX (Compose, WorkManager).
- Veri tek bir yerel JSON dosyasında (`filesDir/odak.json`). Sunucu yok, hesap yok, internet izni yok.
  Tasarım belgesi Room + DataStore öneriyordu; MVP için daha az bağımlılık ve daha az derleme riski olsun diye düz JSON seçildi.
  Kart sayısı binleri geçerse Room'a taşımak kolay: tüm okuma/yazma `data/Repo.kt` içinde.
- **Algılama:** `service/Detectors.kt`. Instagram'da Reels/Keşfet sekmesi, YouTube'da Shorts oynatıcısı görünüm kimliği ve
  seçili sekme metniyle bulunur. Uygulamalar güncellenince kimlikler değişebilir; bir şey kaçarsa önce bu listeleri güncelle.
  TikTok ve diğerleri "uygulamanın tamamı" modunda çalışır.
- Durdurma ekranı ayrı bir görevde (`taskAffinity`) açılır. "Yine de aç" o uygulama oturumu için izin verir; uygulamadan
  30 sn'den uzun çıkınca izin biter.

## Ölçülecekler (MVP geçiş kriteri)
Ana sayfadaki **"Bu haftaki vazgeçme oranın"** satırı: 2 haftada en az %33 (one sec'te %36'ydı).
Ritim ekranındaki SRBAI grafiği: 10 haftada yükselmeli.
