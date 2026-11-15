# Rich Translate

**Penerjemah teks game dan layar untuk Android 11+.**

[Unduh APK](https://github.com/ramax100/Rich-Translate/releases/tag/v0.12.0) · [Catatan perubahan](CHANGELOG.md) · [Dokumentasi teknis](docs/TECHNICAL_NOTES.md)

## Tentang Aplikasi

Rich Translate membantu menerjemahkan teks di layar menggunakan **Google ML Kit offline** atau **DeepL API opsional dengan key sendiri**. Ketuk bola melayang, lingkari tulisan, lalu baca terjemahannya di area teks asli.

Bahasa bawaan **Inggris → Indonesia**, mesin bawaan **ML Kit**. Model ML Kit perlu diunduh sekali; setelah siap, terjemahan dapat digunakan **offline**. DeepL memerlukan internet dan akun/API key sendiri. Akses screenshot melalui Accessibility harus diaktifkan sendiri oleh pengguna.

## Fitur Utama

- **Bola melayang transparan** untuk memilih teks tanpa berpindah aplikasi.
- **Auto dialog (opsional)**: lingkari seluruh kotak dialog sekali, lalu terjemahan diperbarui saat teks berubah. Ketuk hasil untuk melanjutkan game; tekan × untuk berhenti.
- **Seleksi bebas** dengan melingkari tulisan yang ingin diterjemahkan.
- **Hasil di posisi teks asli**, dengan latar putih transparan lembut, tepi halus, dan teks tajam. Ukuran huruf mengikuti perkiraan OCR.
- **ML Kit offline** setelah model bahasa siap, tanpa server penerjemah aplikasi.
- **DeepL API opsional (BYOK)**: input key sendiri, simpan terenkripsi, uji koneksi/penggunaan, dan hapus key. API Free/Pro dipilih otomatis. Kuota/biaya mengikuti akun DeepL kamu.
- **Menu sederhana** untuk bahasa, model offline, akses layar, dan pengaturan.

Latar lembut ini adalah lapisan putih transparan, **bukan blur asli pada gambar game**. Teks terjemahan tidak ikut diburamkan.

Auto dialog diaktifkan lewat **Setelan → Auto dialog** dengan persetujuan terpisah. Pilihan lasso menjadi kotak pantauan, bukan deteksi seluruh layar. Teks harus sama pada dua scan sebelum diterjemahkan, sehingga ada jeda. Android 11–13 dan fallback Android 14+ menyembunyikan hasil sesaat saat scan (dapat berkedip). Android 14+ mencoba screenshot jendela tanpa overlay; jika ukuran jendela tidak cocok, digunakan fallback. Hasil tidak bisa digulir selama auto. Pilih ulang jika posisi kotak dialog berubah.

> OCR yang dibundel mendukung tulisan Latin. Hasil terjemahan dan penempatannya dapat kurang tepat. Layar yang dilindungi dari screenshot tidak didukung. APK saat ini adalah debug build pengujian; belum diuji pada HP fisik. Batasan rekaman/streaming dijelaskan di [dokumentasi teknis](docs/TECHNICAL_NOTES.md).

## Menggunakan DeepL (opsional)

1. Buka **Setelan → Mesin terjemahan → DeepL · API sendiri** dan baca/setujui pengiriman teks.
2. Masukkan **API key DeepL milik sendiri**, lalu **Simpan key**. Akun DeepL biasa belum tentu memiliki akses API.
3. Tekan **Uji koneksi & kuota**. Tombol ini hanya memeriksa penggunaan akun, tidak mengirim contoh terjemahan.
4. Mulai seperti biasa. Mode manual dan Auto dialog memakai mesin yang dipilih. Model bahasa ML Kit tidak diperlukan untuk DeepL; OCR tetap lokal dan mendukung Latin.

**Teks OCR dari area pilihan dikirim langsung ke server DeepL melalui HTTPS; screenshot tidak diunggah.** Key Free dengan akhiran `:fx` memakai `api-free.deepl.com`; key lain memakai `api.deepl.com`. DeepL membutuhkan internet, termasuk saat Auto dialog aktif. Tidak ada fallback ke layanan lain atau retry terjemahan otomatis. Permintaan yang sudah diterima server mungkin tetap memakai kuota walau dibatalkan di aplikasi.

Key tidak dibundel dalam APK/source atau ditampilkan ulang. Penyimpanan memakai AES-GCM dengan Android Keystore; field tersamarkan dan isinya dibersihkan setelah disimpan/halaman ditinggalkan. Enkripsi bukan jaminan keamanan pada perangkat root/terkompromi. **Jangan kirim key ke chat atau membagikan key.** Hapus melalui **Hapus key DeepL**, atau pilih ML Kit untuk kembali offline. Inggris tujuan DeepL memakai EN-US; Portugis PT-PT.

Rujukan DeepL: [Translate API](https://developers.deepl.com/api-reference/translate/request-translation) · [Autentikasi](https://developers.deepl.com/docs/getting-started/auth) · [Penggunaan akun](https://developers.deepl.com/api-reference/usage-and-quota/check-usage-and-limits) · [Privasi](https://www.deepl.com/privacy). Rich Translate tidak berafiliasi dengan DeepL.

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
| `AutoTextTracker.java` / `AutoCapturePolicy.java` | Stabilisasi teks, batas area dan kebijakan capture otomatis |
| `TranslationEngine.java` | Pemilihan ML Kit/DeepL, batch dan cache sementara |
| `DeepLApi.java` / `DeepLSettings.java` / `DeepLKeyStore.java` | API resmi, pengaturan key sendiri dan penyimpanan terenkripsi |
| `FloatingBallView.java` / `LassoView.java` | Bola melayang dan seleksi teks |
| `OverlayHost.java` | Wadah tampilan proses dan hasil |
| `TextPlacement.java` / `TranslationCard.java` | Ukuran, posisi, dan tampilan teks terjemahan |

Petunjuk build dan pemeriksaan tersedia di [catatan teknis](docs/TECHNICAL_NOTES.md).

## Bebas Dimodifikasi

**Kode aplikasi Rich Translate bebas dipelajari dan dimodifikasi.** Kamu boleh menyesuaikan tampilan, fitur, dan alur aplikasi sesuai kebutuhan.

Izin ini berlaku untuk kode aplikasi milik proyek, **bukan izin untuk mengubah lisensi atau mengekstrak kode/model Google ML Kit**. Penggunaan SDK, model, logo, dan komponen pihak ketiga tetap mengikuti ketentuan pemiliknya. Pertahankan atribusi Google yang diwajibkan pada versi modifikasi.

## Google ML Kit — Sumber, Ketentuan, dan Atribusi

Rich Translate menggunakan **ML Kit on-device Translation** untuk terjemahan offline dan **ML Kit Text Recognition** untuk OCR lokal. DeepL adalah pilihan terpisah. Rich Translate bukan produk Google dan tidak berafiliasi dengan Google.

Rujukan resmi:

- [Dokumentasi ML Kit Translation](https://developers.google.com/ml-kit/language/translation)
- [Dokumentasi ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2)
- [Ketentuan layanan dan privasi ML Kit](https://developers.google.com/ml-kit/terms)
- [Ketentuan Google APIs](https://developers.google.com/terms)
- [Panduan penggunaan on-device Translation](https://developers.google.com/ml-kit/language/translation/translation-terms)
- [Persyaratan atribusi Google Translate](https://cloud.google.com/translate/attribution)
- [Kebijakan privasi Google](https://policies.google.com/privacy)

Ketentuan SDK/model berbeda dari lisensi contoh kode pada halaman dokumentasi. Jangan menganggap seluruh SDK atau model ML Kit berlisensi Apache 2.0 hanya karena contoh kodenya memakai lisensi tersebut.

Pemrosesan OCR dan terjemahan **ML Kit** berlangsung di perangkat; terjemahan **DeepL** mengirim teks OCR ke DeepL setelah persetujuan terpisah. SDK Google dapat mengakses jaringan untuk model, pembaruan, dan metrik penggunaan/kinerja sesuai ketentuannya.

<details>
<summary>Disclaimer terjemahan Google</summary>

THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.

</details>

## Channel Telegram

**[Gabung Channel Telegram — @ChRichStore](https://t.me/ChRichStore)**
