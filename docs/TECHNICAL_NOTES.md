# Catatan teknis — Rich Translate 0.11.0

Android 11+ (API 30+). Package `id.richtranslate`, versionCode **11**, versionName `0.11.0`. Mesin terjemahan hanya **Google ML Kit on-device**, default Inggris → Indonesia. Debug build pengujian, bukan rilis produksi/Play Store.

## Auto dialog — 0.11

Default OFF. Setelan → Auto dialog menampilkan persetujuan terpisah sebelum menyimpan pilihan. Mengaktifkan layanan Accessibility saja tidak memulai capture. Ketuk bola, lingkari **seluruh kotak dialog**, lalu area bounding rectangle dipakai untuk scan berikutnya. Tidak ada deteksi perpindahan posisi kotak: pilih ulang jika berubah.

Siklus serial: screenshot → crop/OCR → normalisasi teks → stabilisasi dua scan → terjemahan ML Kit → scan verifikasi sumber → render jika masih sama. Scan selanjutnya dijadwalkan 1.200 ms setelah pekerjaan selesai, bukan timer bertumpuk. Waktu respons tergantung OCR/model/perangkat; bukan terjemahan instan. Efek ketik yang berhenti lebih dari satu siklus masih dapat dianggap stabil. Scene tanpa teks dibersihkan setelah dua scan kosong; teks berbeda membuang hasil lama setelah OCR mendeteksi perubahan. Teks yang sama mengabaikan variasi whitespace/case dan tidak memicu terjemahan ulang. Cache teks/terjemahan hanya di memori.

Pada API 34+ mencoba `takeScreenshotOfWindow` target aplikasi fokus. Buffer harus sama ukuran dengan layar agar pemetaan koordinat aman; ukuran berbeda/error jendela beralih ke screenshot display. Pada API 30–33/fallback, host dan × disembunyikan, menunggu 110 ms lalu screenshot. Dikembalikan saat callback; batas tersembunyi 650 ms mengakhiri auto bila callback terlalu lambat, bukan membiarkan stop tersembunyi atau menangkap overlay sendiri. Kedip dimungkinkan dan disetujui pengguna. Tetap menghormati layar terlindungi; tidak mengakali FLAG_SECURE.

Metadata jendela didapat melalui `getWindows()`, membutuhkan `canRetrieveWindowContent=true` dan `flagRetrieveInteractiveWindows`. Implementasi **tidak** memanggil getRootInActiveWindow/getRoot/getText ataupun membaca node aplikasi; izin sistem ini lebih luas dari pemakaian metadata saja. Android/OEM tertentu dapat meminta Accessibility dimatikan/dihidupkan lagi setelah update konfigurasi layanan.

Auto memakai host fullscreen `FLAG_NOT_TOUCHABLE` dan sebuah jendela × kecil yang touchable di sudut di luar area pilihan. Tidak ada injeksi/forwarding gesture buatan: Android meneruskan sentuhan langsung ke game di bawah host hasil. Karena itu strip tidak dapat di-scroll. Hanya area × tetap mengambil sentuhan. Manual tetap satu host touchable dan bisa scroll. Pemindaian berhenti saat ×/Hentikan/toggle OFF/kunci layar/rotasi/target berubah/error. Epoch guards membuang callback lama; screenshot/crop di-recycle, buffer ditutup. Timeout satu siklus 15 detik; capture display yang terlalu lambat dibatalkan lebih cepat agar tombol stop kembali tersedia.

Belum diuji langsung pada HP, game nyata, Accessibility window screenshot, ML Kit end-to-end, atau rekaman. Tes kebijakan/tampilan/siklus memakai simulasi Robolectric dan tes unit, bukan bukti kompatibilitas seluruh perangkat.

## Perubahan 0.10

Latar strip hasil kini putih transparan alpha 232/255 (sekitar 91% opak), tepi feather maksimum 2 dp dan sudut membulat. Ini bukan blur asli di belakang window. Glyph tetap alpha 255, warna RGB 20/20/20; tidak ada filter atau shader blur pada teks. Latar sumber dan hasil di-union menjadi satu path untuk mencegah alpha bertumpuk. Path disimpan dan dihitung ulang hanya ketika scroll berubah, bukan pada setiap draw. Bagian terjemahan yang jauh di luar viewport tidak dimasukkan ke path yang digambar. Tidak ada screenshot tambahan, bitmap mask, layer software atau efek yang membutuhkan Android 12+.

Padding teks tetap 2 dp dengan ruang tepi tambahan 2 dp agar feather tidak terpotong pada batas biasa; glyph tetap sejajar lokasi sumber. Di batas layar/area scroll, clipping tetap diperlukan. Latar yang transparan dapat memperlihatkan sedikit gambar/tulisan asli di bawahnya; ini bukan inpainting.

Tambahan 7 tes: alpha latar, union tanpa overdraw ganda, feather tepi, glyph tajam/solid, tanpa screenshot buffer/software layer, draw statis, serta kontras di atas area gelap. Tampilan simulasi berada di images/result-soft.png; itu bukan hasil OCR di HP.

## Perubahan sebelumnya — 0.9

Menu Utama dan Setelan dipisah. Slogan, panel contoh, petunjuk panjang berulang dan status model ganda dihapus dari dashboard. Bantuan, disclosure Accessibility dan atribusi tetap tersedia. Unduh model disembunyikan jika model siap. Pemilih bahasa ditumpuk pada layar sempit atau font scale besar. Tab dipertahankan saat activity dibuat ulang. Hanya Mulai atau Hentikan yang ditampilkan sesuai sesi.

Tambahan 8 tes menu mencakup tab, pengaturan, state recreation, status unduhan dan layout 320 dp. Render UI pada direktori images berasal dari Robolectric, bukan uji HP fisik. Pesan model belum dapat diperiksa pada render adalah keterbatasan lingkungan tes.

## Perubahan yang dipertahankan dari 0.8

- **Integrasi server Argos/LibreTranslate dihapus**, termasuk pemilih mesin, kolom URL/API key, tombol uji, persetujuan upload, HTTP client dan panduan server dari source versi ini.
- Aplikasi kembali langsung memakai ML Kit. Teks OCR dan screenshot tidak dikirim ke backend penerjemah aplikasi. SDK Google tetap dapat memakai jaringan untuk unduhan model/diagnostik sesuai kebijakannya.
- Saat aplikasi atau layanan dijalankan, setelan server lama (URL, API key, izin pengiriman dan pilihan mesin) dibersihkan. Bahasa sumber/tujuan, model ML Kit yang sudah tersedia, izin Accessibility, mode streaming dan pilihan Wi-Fi dipertahankan. Error lama terkait konfigurasi yang dihapus juga dibersihkan.
- **Floating ball abu-abu gelap netral, 40% opacity / 60% transparan**: ARGB `#66282828`. Bukan lagi isi burgundy yang sangat samar. Pinggir putih transparan dipertahankan agar tetap terlihat di layar gelap.
- Ukuran tidak berubah: visual **32 dp**, area sentuh **40 dp**, tanpa huruf atau bayangan. Ketuk memilih teks, geser memindahkan, tekan lama menghentikan sesi.

Tidak ada server eksternal pengguna yang dimatikan/dihapus: versi sebelumnya hanya menyediakan klien dan tidak pernah memasang server pengguna. Perubahan ini menghapus integrasi dari aplikasi baru. Arsip laporan/source versi lama, bila masih ada di workspace, adalah artefak historis, bukan fitur APK 0.8.

## Fitur yang dipertahankan

- Garis seleksi **merah #E53935** dan logo Rich Translate.
- Screenshot manual setelah pengguna melingkari teks; mode Auto dialog memindai berkala hanya setelah persetujuan dan pemilihan area. Tidak memakai MediaProjection atau izin SYSTEM_ALERT_WINDOW.
- Hasil strip **putih transparan lembut per baris, teks hitam tajam** di posisi teks asal. Ukuran diperkirakan dari tinggi glyph OCR dan skala screenshot, bukan ukuran tetap/minimal 14 sp.
- Terjemahan lebih panjang bisa diperkecil sampai sekitar 22%, lalu wrapping lokal. Tinggi pertumbuhan dibatasi dekat sumber/sebelum blok lain. Teks overflow tetap lengkap dan dapat digulir dalam mode manual; mode auto memprioritaskan ketukan ke game. Blok terpisah tidak digabung otomatis.
- Mode manual: satu host overlay untuk proses/hasil, bukan satu jendela per blok. × tetap pada host yang sama; tidak ada koreksi posisi per blok via updateViewLayout atau pemasangan ulang × saat hasil muncul. Toast hasil panjang tetap dihapus.
- Host tidak mengambil fokus game. Pada mode manual, sentuhan ke game ditahan selama hasil terbuka; tekan × untuk bermain lagi. Ini tidak otomatis menghentikan animasi/game di belakang.
- Tidak ada screenshot beku atau dimming fullscreen; window animation 0. Perubahan ini mengurangi sumber pergantian surface dalam kode, bukan jaminan semua kedip/FPS telah teratasi pada HP.

Ukuran/posisi hasil adalah perkiraan OCR, bukan deteksi font asli game. Ketebalan, font, outline, perspektif, jarak huruf dan pembagian baris tidak dijamin identik. Terjemahan yang lebih panjang tidak selalu muat di area/font yang persis sama.

## Instalasi dan penggunaan

1. Hentikan sesi lama, pasang **RichTranslate-0.11.0.apk** sebagai pembaruan Rich Translate versi sebelumnya. Package dan sertifikat sama, versionCode meningkat. Tidak perlu uninstall untuk mempertahankan data.
2. Buka Rich Translate. Pengaturan server lama dibersihkan otomatis. Pilih pasangan bahasa dan tekan **Siapkan bahasa offline** bila model belum siap. Jika sebelumnya hanya memakai server, model ML Kit mungkin perlu diunduh terlebih dahulu.
3. Unduhan pertama memerlukan internet; setelah model siap, terjemahan bisa offline. Model terjemahan tidak dibundel dalam APK. Resource OCR Latin dan mesin native dibundel.
4. Pastikan **Rich Translate • terjemahan layar** aktif di Setelan → Accessibility. Pada Android tertentu, sideload mungkin dibatasi; hanya izinkan setelan terbatas bila memahami dan memercayai APK.
5. Mulai floating ball, buka game, ketuk bola abu-abu, lingkari seluruh tulisan dan lepas jari. Pilih dialog stabil atau pause game manual.
6. Manual: baca/scroll strip putih, tekan × untuk kembali ke bola. Auto: aktifkan di Setelan, pilih seluruh kotak dialog, tap hasil untuk lanjut cerita; × mengakhiri pantauan. Geser bola untuk pindah, tekan lama menghentikan sesi.

Dari aplikasi lama sebelum merek Rich Translate, package berbeda; data/izin tidak dijamin berpindah. Nonaktifkan layanan lama agar tidak ada dua bola.

## Kedip dan Mode streaming

Default Mode streaming OFF. Untuk memeriksa kedip, uji OFF dahulu, hentikan sesi dan mulai ulang setelah perubahan mode. Bandingkan layar fisik dengan hasil rekaman.

Jika ON, FLAG_SECURE tetap dipakai. Karena host proses/hasil fullscreen, **perekam/OEM dapat menghitamkan seluruh frame selama seleksi, pemrosesan atau hasil tampil**, walaupun layar fisik normal. Ini konsekuensi proteksi window, bukan jaminan overlay hilang bersih dari semua perekam/streamer. Kamera eksternal tetap bisa melihat layar fisik.

Belum ada detail HP/Android/game/perekam atau reproduksi perangkat untuk laporan kedip sebelumnya. Jika masih terjadi, sertakan detail tersebut, status streaming, tahap tepatnya dan apakah terlihat langsung atau hanya rekaman. Tidak ada klaim bebas bug/kedip atau jaminan layanan tidak pernah dihentikan OEM/Android.

## Pemeriksaan versi ini

Build final `assembleDebug testDebugUnitTest lintDebug`: **BUILD SUCCESSFUL**.

- **88 tes lulus**, 0 gagal/error/skipped: 66 tes sebelumnya, 10 tes AutoTextTracker, 10 tes AutoMode, dan dua konfigurasi SDK tambahan untuk metadata Accessibility (API 30/31/34).
- Tes auto mencakup stabilisasi/efek ketik, deduplikasi, scene kosong/berulang, pembatalan epoch, persetujuan terpisah, rectangle area, aturan fallback screenshot serta pemisahan flag host hasil dan tombol stop. Pengujian flag bukan uji sentuhan lintas jendela pada HP.
- Tes bola diperbarui: alpha pusat 102/255, RGB netral, ukuran tetap, pinggir area sentuh transparan, kontras pada latar terang dan gelap.
- Tes migrasi memastikan pengaturan/kredensial server terhapus, setelan pengguna penting tetap, pembersihan idempotent dan startup layanan ikut membersihkan.
- Tes stabilitas host, pemetaan posisi, layout white/black, scrolling hasil, garis merah, bounded gesture dan lifecycle sebelumnya tetap lulus.
- Android Lint **0 error, 18 warning**. Rincian asli disertakan; warning berupa rekomendasi dependency/UI, lokalisasi string, constructor layout-editor dan resource adaptive icon.
- APK signature v2 diverifikasi. Sertifikat debug SHA-256 `773b818aea311a87fe553e6df35614f8e1227f88cc0677aa8d8e1c042e49b6e4`.
- ARM64 + ARM32, minSdk 30, target/compile 34, JNI/DEX dikompresi. Penyimpanan instalasi lebih besar daripada file APK; model terjemahan terpisah.
- Build awal mengalami Gradle daemon terhenti; percobaan metaspace 192 MiB juga kekurangan ruang. Build final memakai heap Gradle 384 MiB/metaspace 256 MiB, ActiveProcessorCount=2, serta tes 384 MiB/fork per kelas. Ini masalah proses build, bukan bukti crash aplikasi di HP.

**Belum diuji di HP fisik, game/rekaman nyata atau OCR/ML Kit end-to-end.** Tes JVM dan render Robolectric tidak membuktikan semua bug hilang. Contoh layout hasil dalam laporan adalah teks/kotak manual, bukan OCR gambar pengguna. Gambar halaman utama dapat menunjukkan SDK model belum tersedia karena lingkungan tes tidak menjalankan inferensi ML Kit.

## Privasi dan batasan

Accessibility tidak membaca node teks aplikasi dan tidak melakukan gesture otomatis. Tidak membypass FLAG_SECURE game. OCR yang dibundel Latin; pilihan bahasa terjemahan tidak membuat OCR semua sistem tulisan tersedia. Screenshot/cache berada di memori, error terakhir disimpan lokal tanpa riwayat OCR. Tidak ada backend, iklan atau analytics buatan aplikasi. SDK pihak ketiga tunduk kebijakan Google. Terjemahan dapat salah dan tidak cocok untuk keputusan kritis.

## Build source

JDK 17, Gradle wrapper 8.7, AGP 8.5.2, SDK/build-tools 34. Android Studio membuat local.properties sesuai SDK lokal.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Untuk lingkungan memori terbatas gunakan `-Dorg.gradle.jvmargs='-Xmx384m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8'`. Tes dibatasi 384 MiB dan fork per kelas. Windows: `gradlew.bat`. Unduhan dependency pertama perlu internet. Source tidak menyertakan signing key. Publikasi produksi membutuhkan release signing, kepatuhan target SDK/Accessibility/privasi yang berlaku dan pengujian perangkat.

## Atribusi

Terjemahan oleh Google Translate melalui ML Kit on-device Translation; OCR oleh Google ML Kit. Badge resmi Google dipertahankan. Rich Translate bukan produk Google dan tidak berafiliasi dengannya.

- https://developers.google.com/ml-kit/language/translation
- https://developers.google.com/ml-kit/language/translation/translation-terms
- https://cloud.google.com/translate/attribution
- https://developers.google.com/ml-kit/terms
- https://policies.google.com/privacy

> THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
