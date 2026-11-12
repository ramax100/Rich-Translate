# Rich Translate

**Penerjemah teks game dan layar untuk Android 11+.**

[Unduh APK](https://github.com/ramax100/Rich-Translate/releases/tag/v0.10.0) · [Catatan perubahan](CHANGELOG.md) · [Dokumentasi teknis](docs/TECHNICAL_NOTES.md)

## Tentang Aplikasi

Rich Translate membantu menerjemahkan teks di layar menggunakan **Google ML Kit**. Ketuk bola melayang, lingkari tulisan, lalu baca terjemahannya di area teks asli.

Bahasa bawaan **Inggris → Indonesia**. Model bahasa perlu diunduh sekali; setelah siap, terjemahan dapat digunakan **offline**. Akses screenshot melalui Accessibility harus diaktifkan sendiri oleh pengguna.

## Fitur Utama

- **Bola melayang transparan** untuk memilih teks tanpa berpindah aplikasi.
- **Seleksi bebas** dengan melingkari tulisan yang ingin diterjemahkan.
- **Hasil di posisi teks asli**, dengan latar putih transparan lembut, tepi halus, dan teks tajam. Ukuran huruf mengikuti perkiraan OCR.
- **Terjemahan offline** setelah model bahasa siap, tanpa server penerjemah aplikasi.
- **Menu sederhana** untuk bahasa, model offline, akses layar, dan pengaturan.

Latar lembut ini adalah lapisan putih transparan, **bukan blur asli pada gambar game**. Teks terjemahan tidak ikut diburamkan.

> OCR yang dibundel mendukung tulisan Latin. Hasil terjemahan dan penempatannya dapat kurang tepat. Layar yang dilindungi dari screenshot tidak didukung. APK saat ini adalah debug build pengujian; belum diuji pada HP fisik. Batasan rekaman/streaming dijelaskan di [dokumentasi teknis](docs/TECHNICAL_NOTES.md).

## Struktur Proyek

```text
Rich-Translate/
├── app/
│   ├── src/main/
│   │   ├── java/id/richtranslate/   # Kode aplikasi
│   │   ├── res/                    # Ikon, tema, dan konfigurasi
│   │   └── AndroidManifest.xml     # Izin dan komponen Android
│   ├── src/test/                   # Tes otomatis
│   └── build.gradle               # Dependency dan versi aplikasi
├── branding/                      # Logo asli
├── docs/                          # Catatan teknis dan gambar UI
├── gradle/                        # Gradle wrapper
├── CHANGELOG.md                   # Riwayat perubahan
└── README.md
```

Komponen utama dalam `java/id/richtranslate/`:

| File | Fungsi |
| --- | --- |
| `MainActivity.java` | Menu utama dan pengaturan |
| `RichAccessibilityService.java` | Screenshot, alur OCR, dan overlay |
| `TranslationEngine.java` | Terjemahan offline melalui ML Kit |
| `FloatingBallView.java` / `LassoView.java` | Bola melayang dan seleksi teks |
| `OverlayHost.java` | Wadah tampilan proses dan hasil |
| `TextPlacement.java` / `TranslationCard.java` | Ukuran, posisi, dan tampilan teks terjemahan |

Petunjuk build dan pemeriksaan tersedia di [catatan teknis](docs/TECHNICAL_NOTES.md).

## Bebas Dimodifikasi

**Kode aplikasi Rich Translate bebas dipelajari dan dimodifikasi.** Kamu boleh menyesuaikan tampilan, fitur, dan alur aplikasi sesuai kebutuhan.

Izin ini berlaku untuk kode aplikasi milik proyek, **bukan izin untuk mengubah lisensi atau mengekstrak kode/model Google ML Kit**. Penggunaan SDK, model, logo, dan komponen pihak ketiga tetap mengikuti ketentuan pemiliknya. Pertahankan atribusi Google yang diwajibkan pada versi modifikasi.

## Google ML Kit — Sumber, Ketentuan, dan Atribusi

Rich Translate menggunakan **ML Kit on-device Translation** untuk terjemahan dan **ML Kit Text Recognition** untuk OCR. Rich Translate bukan produk Google dan tidak berafiliasi dengan Google.

Rujukan resmi:

- [Dokumentasi ML Kit Translation](https://developers.google.com/ml-kit/language/translation)
- [Dokumentasi ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2)
- [Ketentuan layanan dan privasi ML Kit](https://developers.google.com/ml-kit/terms)
- [Ketentuan Google APIs](https://developers.google.com/terms)
- [Panduan penggunaan on-device Translation](https://developers.google.com/ml-kit/language/translation/translation-terms)
- [Persyaratan atribusi Google Translate](https://cloud.google.com/translate/attribution)
- [Kebijakan privasi Google](https://policies.google.com/privacy)

Ketentuan SDK/model berbeda dari lisensi contoh kode pada halaman dokumentasi. Jangan menganggap seluruh SDK atau model ML Kit berlisensi Apache 2.0 hanya karena contoh kodenya memakai lisensi tersebut.

Pemrosesan OCR dan terjemahan berlangsung di perangkat. SDK Google dapat mengakses jaringan untuk model, pembaruan, dan metrik penggunaan/kinerja sesuai ketentuannya.

<details>
<summary>Disclaimer terjemahan Google</summary>

THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.

</details>

## Channel Telegram

**[Gabung Channel Telegram — @ChRichStore](https://t.me/ChRichStore)**
