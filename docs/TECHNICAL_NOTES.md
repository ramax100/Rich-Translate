# Catatan teknis — Rich Translate 0.10.0

Android 11+ (API 30+). Package `id.richtranslate`, versionCode **10**, versionName `0.10.0`. Mesin terjemahan hanya **Google ML Kit on-device**, default Inggris → Indonesia. Debug build pengujian, bukan rilis produksi/Play Store.

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
- Screenshot Accessibility hanya setelah pengguna melingkari teks dan melepas jari; bukan rekaman kontinu. Tidak memakai MediaProjection atau izin SYSTEM_ALERT_WINDOW.
- Hasil strip **putih transparan lembut per baris, teks hitam tajam** di posisi teks asal. Ukuran diperkirakan dari tinggi glyph OCR dan skala screenshot, bukan ukuran tetap/minimal 14 sp.
- Terjemahan lebih panjang bisa diperkecil sampai sekitar 22%, lalu wrapping lokal. Tinggi pertumbuhan dibatasi dekat sumber/sebelum blok lain. Teks overflow tetap lengkap dan dapat digulir. Blok terpisah tidak digabung otomatis.
- Satu host overlay untuk proses/hasil, bukan satu jendela per blok. × tetap pada host yang sama; tidak ada koreksi posisi per blok via updateViewLayout atau pemasangan ulang × saat hasil muncul. Toast hasil panjang tetap dihapus.
- Host tidak mengambil fokus game. Sentuhan ke game ditahan selama hasil terbuka; tekan × untuk bermain lagi. Ini tidak otomatis menghentikan animasi/game di belakang.
- Tidak ada screenshot beku atau dimming fullscreen; window animation 0. Perubahan ini mengurangi sumber pergantian surface dalam kode, bukan jaminan semua kedip/FPS telah teratasi pada HP.

Ukuran/posisi hasil adalah perkiraan OCR, bukan deteksi font asli game. Ketebalan, font, outline, perspektif, jarak huruf dan pembagian baris tidak dijamin identik. Terjemahan yang lebih panjang tidak selalu muat di area/font yang persis sama.

## Instalasi dan penggunaan

1. Hentikan sesi lama, pasang **RichTranslate-0.10.0.apk** sebagai pembaruan Rich Translate 0.5/0.6/0.7. Package dan sertifikat sama, versionCode meningkat. Tidak perlu uninstall untuk mempertahankan data.
2. Buka Rich Translate. Pengaturan server lama dibersihkan otomatis. Pilih pasangan bahasa dan tekan **Siapkan bahasa offline** bila model belum siap. Jika sebelumnya hanya memakai server, model ML Kit mungkin perlu diunduh terlebih dahulu.
3. Unduhan pertama memerlukan internet; setelah model siap, terjemahan bisa offline. Model terjemahan tidak dibundel dalam APK. Resource OCR Latin dan mesin native dibundel.
4. Pastikan **Rich Translate • terjemahan layar** aktif di Setelan → Accessibility. Pada Android tertentu, sideload mungkin dibatasi; hanya izinkan setelan terbatas bila memahami dan memercayai APK.
5. Mulai floating ball, buka game, ketuk bola abu-abu, lingkari seluruh tulisan dan lepas jari. Pilih dialog stabil atau pause game manual.
6. Baca/scroll strip putih, tekan × untuk kembali ke bola. Geser bola untuk pindah, tekan lama menghentikan sesi.

Dari aplikasi lama sebelum merek Rich Translate, package berbeda; data/izin tidak dijamin berpindah. Nonaktifkan layanan lama agar tidak ada dua bola.

## Kedip dan Mode streaming

Default Mode streaming OFF. Untuk memeriksa kedip, uji OFF dahulu, hentikan sesi dan mulai ulang setelah perubahan mode. Bandingkan layar fisik dengan hasil rekaman.

Jika ON, FLAG_SECURE tetap dipakai. Karena host proses/hasil fullscreen, **perekam/OEM dapat menghitamkan seluruh frame selama seleksi, pemrosesan atau hasil tampil**, walaupun layar fisik normal. Ini konsekuensi proteksi window, bukan jaminan overlay hilang bersih dari semua perekam/streamer. Kamera eksternal tetap bisa melihat layar fisik.

Belum ada detail HP/Android/game/perekam atau reproduksi perangkat untuk laporan kedip sebelumnya. Jika masih terjadi, sertakan detail tersebut, status streaming, tahap tepatnya dan apakah terlihat langsung atau hanya rekaman. Tidak ada klaim bebas bug/kedip atau jaminan layanan tidak pernah dihentikan OEM/Android.

## Pemeriksaan versi ini

Build final `assembleDebug testDebugUnitTest lintDebug`: **BUILD SUCCESSFUL**.

- **66 tes lulus**, 0 gagal/error: Geometry 5, GestureTrace 5, InlineLayout 19, Overlay/UI 12, OverlayHost 7, OfflineMigration 3, MenuLayout 8, SoftBackground 7.
- Tes bola diperbarui: alpha pusat 102/255, RGB netral, ukuran tetap, pinggir area sentuh transparan, kontras pada latar terang dan gelap.
- Tes migrasi memastikan pengaturan/kredensial server terhapus, setelan pengguna penting tetap, pembersihan idempotent dan startup layanan ikut membersihkan.
- Tes stabilitas host, pemetaan posisi, layout white/black, scrolling hasil, garis merah, bounded gesture dan lifecycle sebelumnya tetap lulus.
- Android Lint **0 error, 18 warning**. Rincian asli disertakan; warning berupa rekomendasi dependency/UI, lokalisasi string, constructor layout-editor dan resource adaptive icon.
- APK signature v2 diverifikasi. Sertifikat debug SHA-256 `773b818aea311a87fe553e6df35614f8e1227f88cc0677aa8d8e1c042e49b6e4`.
- ARM64 + ARM32, minSdk 30, target/compile 34, JNI/DEX dikompresi. Penyimpanan instalasi lebih besar daripada file APK; model terjemahan terpisah.
- Build awal mengalami Gradle daemon terhenti di lingkungan build. Pengulangan dengan heap Gradle 384 MiB serta tes 384 MiB/fork per kelas berhasil. Ini masalah proses build, bukan bukti crash aplikasi di HP.

**Belum diuji di HP fisik, game/rekaman nyata atau OCR/ML Kit end-to-end.** Tes JVM dan render Robolectric tidak membuktikan semua bug hilang. Contoh layout hasil dalam laporan adalah teks/kotak manual, bukan OCR gambar pengguna. Gambar halaman utama dapat menunjukkan SDK model belum tersedia karena lingkungan tes tidak menjalankan inferensi ML Kit.

## Privasi dan batasan

Accessibility tidak membaca node teks aplikasi dan tidak melakukan gesture otomatis. Tidak membypass FLAG_SECURE game. OCR yang dibundel Latin; pilihan bahasa terjemahan tidak membuat OCR semua sistem tulisan tersedia. Screenshot/cache berada di memori, error terakhir disimpan lokal tanpa riwayat OCR. Tidak ada backend, iklan atau analytics buatan aplikasi. SDK pihak ketiga tunduk kebijakan Google. Terjemahan dapat salah dan tidak cocok untuk keputusan kritis.

## Build source

JDK 17, Gradle wrapper 8.7, AGP 8.5.2, SDK/build-tools 34. Android Studio membuat local.properties sesuai SDK lokal.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Untuk lingkungan memori terbatas gunakan `-Dorg.gradle.jvmargs='-Xmx384m -XX:MaxMetaspaceSize=256m -Dfile.encoding=UTF-8'`. Tes dibatasi 384 MiB dan fork per kelas. Windows: `gradlew.bat`. Unduhan dependency pertama perlu internet. Source tidak menyertakan signing key. Publikasi produksi membutuhkan release signing, kepatuhan target SDK/Accessibility/privasi yang berlaku dan pengujian perangkat.

## Atribusi

Terjemahan oleh Google Translate melalui ML Kit on-device Translation; OCR oleh Google ML Kit. Badge resmi Google dipertahankan. Rich Translate bukan produk Google dan tidak berafiliasi dengannya.

- https://developers.google.com/ml-kit/language/translation
- https://developers.google.com/ml-kit/language/translation/translation-terms
- https://cloud.google.com/translate/attribution
- https://developers.google.com/ml-kit/terms
- https://policies.google.com/privacy

> THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
