# Catatan perubahan

## 0.11.0

- Auto dialog opsional di Setelan dengan persetujuan pemindaian berkala terpisah.
- Lingkari seluruh kotak dialog sekali; area tersebut dipantau saat percakapan berganti.
- Dua hasil OCR yang sama diperlukan sebelum terjemahan; teks yang sama tidak diterjemahkan ulang.
- Hasil terjemahan diperiksa ulang terhadap sumber sebelum ditampilkan; hasil lama dibersihkan saat perubahan terdeteksi atau dua scan kosong.
- Ketukan hasil diteruskan ke game; × tetap dapat disentuh untuk menghentikan auto. Scroll hanya di mode manual.
- Android 14+ mencoba screenshot jendela game tanpa isi overlay; Android 11–13/fallback menyembunyikan hasil sesaat saat scan.
- Pemindaian serial, timeout dan pembatalan saat berhenti, kunci layar, perubahan jendela atau rotasi.
- Metadata jendela dipakai untuk memilih target; tidak membaca node/hierarki teks atau melakukan gestur otomatis.

## 0.10.0

- Latar hasil menjadi putih sedikit transparan dengan sudut membulat dan tepi lembut.
- Teks tetap gelap, tajam dan tidak ikut diburamkan.
- Lapisan sumber dan terjemahan digabung agar area tumpang tindih tidak menjadi putih pekat.
- Tidak memakai blur gambar game, filter teks, screenshot beku atau software layer.
- Menu ringkas, bola abu-abu dan ML Kit offline tetap dipertahankan.

## 0.9.0

- Menu dibagi menjadi tab Utama dan Setelan.
- Slogan, panel contoh, petunjuk berulang dan status model ganda dihapus dari dashboard.
- Tombol Unduh model disembunyikan ketika model siap.
- Tombol Mulai/Hentikan ditampilkan bergantian sesuai status sesi.
- Pemilih bahasa ditumpuk pada layar sempit/font scale besar.
- Tab aktif dipertahankan saat activity dibuat ulang.
- Penjelasan izin, privasi, streaming dan atribusi tetap di Bantuan.
- Tambahan 8 tes menu; total 59 tes lulus, lint 0 error / 18 warning.
- Source dan APK dipublikasikan ke GitHub Releases. APK merupakan debug build pengujian, bukan produksi.

## 0.8.0

- Integrasi server Argos/LibreTranslate dihapus.
- Setelan/kredensial server lama dibersihkan saat startup.
- Floating ball menjadi abu-abu gelap 40% opacity, visual 32 dp.

## 0.7.0

- Satu host overlay untuk proses dan seluruh hasil.
- × tidak lagi dilepas/dipasang ulang saat hasil muncul.
- Koreksi posisi per blok dan toast hasil panjang dihapus.

## 0.6.0

- Hasil menempel pada teks asal dengan strip putih per baris.
- Ukuran huruf mengikuti perkiraan glyph OCR, bukan kartu besar berukuran tetap.

## 0.5.0

- Rebrand Rich Translate, package id.richtranslate dan logo pengguna.
- Garis seleksi merah.
