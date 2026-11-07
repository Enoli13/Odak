# Odak — Scroll'dan Çalışmaya (MVP)

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
| Sınav planlayıcı, gri ekran, limitler, gece modu, akşam kartı | v2 | — |
| PDF'den kart, destekçi modu | v3 | — |

Ek: desteye **toplu kart yapıştırma** (`soru | cevap | neden?` her satıra bir kart) ve isteğe bağlı küçük bir örnek deste.

## APK alma

### Yol 1 — GitHub Releases
1. Bu klasörü yeni bir GitHub deposuna yükle.
2. **Actions** sekmesinde "APK derle" iş akışı kendiliğinden çalışır.
3. `v` ile başlayan her etiket (ör. `v0.1.0`) APK ile birlikte Releases sayfasına otomatik yayınlanır.

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
