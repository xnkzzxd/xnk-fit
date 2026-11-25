# Keputusan untuk Pemilik — XNK Fit

Ringkasan untuk Anda (non-teknis) tentang keputusan yang diambil Claude saat Anda tidak ada. Semua isi dikutip dari dokumen sumber; tidak ada keputusan yang diubah di sini. Dibuat 2026-11-25.

**Cara membaca.** Aturan kerja sesi (Decisions.md, aturan 1–5): selalu pilih opsi paling aman, tidak pernah melonggarkan batas keselamatan, tidak pernah mengarang angka atau sumber. Jadi hampir semua pilihan Claude di bawah ini adalah sisi yang lebih ketat. "Baris D-nn" berarti nomor baris di [Decisions.md](Decisions.md); "Rules §" berarti [Rules.md](Rules.md). Tabel pemetaan lengkap ada di Lampiran B.

---

## 1. Perlu Anda putuskan

Urut dari yang paling berisiko bagi pengguna ke yang ringan. Kolom "Batal" berisi berkas atau baris yang diubah.

### A. Keselamatan pengguna

| # | Keputusan | Kenapa penting | Pilihan sekarang | Alternatif | Dampak bila diubah | Batal |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | **Batas usia: di bawah 18 dan di bawah 13** | Remaja tidak boleh mendapat defisit kalori | Umur dihitung "paling muda mungkin" (tahun sekarang − tahun lahir − 1). Lahir 2008 tetap dianggap di bawah 18 sepanjang 2026; lahir 2013 dihentikan sebagai di bawah 13. Di bawah 18: mode ringan, tanpa defisit. Di bawah 13: aplikasi berhenti dan tidak menyimpan apa pun (Rules R_AGE, R_GATE_U13, R_GATE_U18, §12 #1 dan #9; D-21, D-31) | Tanya bulan lahir agar umur tepat; atau blokir tujuan "turun" untuk di bawah 18 (Rules §12 #1) | Melonggarkan margin berarti anak 17 tahun bisa lolos sebagai 18 | `R_AGE_GATE_MARGIN_YEARS = 0` di `domain/rules/BodyRules.kt`; tombol "Ubah tahun lahir" di `Under13Step` |
| 2 | **Hamil dan menyusui** | Tidak ada sumber yang membolehkan defisit | Satu pertanyaan "Hamil atau menyusui?"; hasilnya tanpa defisit, berat tidak ditargetkan turun, "Tanyakan ke dokter atau bidan". Menyusui ikut aturan hamil (Rules R_GATE_PREG, §12 #8; D-29) | Memisahkan menyusui dari hamil | Salah ketat/longgar langsung memengaruhi ibu dan bayi | Teks `sc_out_*` di `strings.xml` (D-29); logika di `domain` |
| 3 | **Riwayat gangguan makan** | Menampilkan kalori bisa membahayakan | Target dipertahankan, **mode tanpa angka** (kalori disembunyikan, hanya rencana makan dan centang), anjuran bicara ke dokter. Tidak ada pedoman klinis yang mengatur mode ini; ini aturan aman buatan pemilik (Rules R_GATE_ED, G4) | Tetap tampilkan angka dengan peringatan | Melonggarkan = angka kalori muncul bagi orang yang tidak seharusnya | Baris R_GATE_ED di Rules dan kode `domain.rules` |
| 4 | **Lantai kalori** | Mencegah target terlalu rendah | Perempuan 1.200, laki-laki 1.500 kcal/hari, dan target tidak pernah di bawah BMR; lantai dibulatkan ke atas (Rules R_FLOOR_F/M/BMR, R_ROUND). Rentang sumber S5: 1.000–1.200 (P) dan 1.200–1.600 (L) | Angka lain (Rules §12 #2) | Menurunkan = target lebih rendah dari yang dianjurkan sumber | Konstanta R_FLOOR_* di `domain/rules` dan Rules |
| 5 | **Beban setelah libur ≥ 14 hari: 60 % dari beban terakhir, 1 set lebih sedikit** ⚠ | Terlalu berat saat kembali bisa cedera. **Tidak ada sumber** (Rules G9, R_TRN_RETURN) | Aturan konservatif milik pemilik, dibiarkan apa adanya; dibulatkan ke bawah ke kelipatan alat | Persentase lain, atau minta pengguna pilih | Mengubah angka tanpa sumber; tesnya `R_TRN_RETURN_*` sengaja `@Ignore` (D-62) | Rules R_TRN_RETURN dan konstanta di `domain/training`; ganti nama tes `sessionTargets_…` setelah G9 ditutup |
| 6 | **Aturan skrining** | Menentukan siapa dapat program/target | Semua tujuh pertanyaan harus dijawab, jawaban "ya" pada risiko → program ringan level 1 + "Konsultasi dokter sebelum latihan"; di onboarding aktivitas dianggap `JARANG` karena baru ditanya kemudian (Rules R_GATE_SCREEN, R_GATE_RISK; D-29, D-30). Batas ketik (tinggi 100–250 cm dst.) hanya penangkap salah ketik (R_INPUT) | Teks hasil dan batas lain | Aktivitas tidak mengubah gerbang mana yang berlaku, hanya kalori | `sc_out_*` di `strings.xml`; `evaluateTargets` (D-30) |
| 7 | **Pengurang defisit "-10 %" untuk jawaban risiko** | Batas keselamatan untuk kelompok "lain-lain" | Defisit maksimum −10 % dari kebutuhan (Rules R_DEF_CAP, PRD S-7); pace turun cepat diperingatkan bila rata-rata > 1 %/minggu selama dua minggu (R_PACE) | — | — | Rules R_DEF_CAP/R_PACE dan `domain/rules` |

### B. Angka tanpa sumber (⚠, bukan final)

| # | Keputusan | Kenapa penting | Pilihan sekarang | Alternatif | Dampak bila diubah | Batal |
| --- | --- | --- | --- | --- | --- | --- |
| 8 | **Ambang level (R_LEVEL)**: 0, 100, 250, 450, 700, 1.000, 1.400, 1.900, 2.500, lalu +600 per level | Hanya soal kecepatan naik level, bukan keselamatan | Dipakai sesuai tertulis; tesnya `@Ignore("unverified")` (Rules §12 #3; D-54) | Kurva lain | Level pengguna lama bergeser | `R_LEVEL_THRESHOLDS` di `domain/rules/ProgressRules.kt` + aktifkan lagi tes |
| 9 | **Anggaran Hemat / Standar / Bebas (R_BUDGET)** dan **tingkat tiap paket (`foodTier`)** | Memengaruhi menu yang disarankan; tingkat adalah penilaian relatif, bukan harga | Biaya 300 per tingkat di atas pilihan, 20 per tingkat di bawah; tanpa angka Rupiah (Rules R_BUDGET; Content §4.5, §8 C-11) | Susun ulang tingkat paket | Menu otomatis berubah | `Planner.kt` (konstanta) dan `foodTier` di pembuat paket (D-37) |
| 10 | **Bobot planner R_PLAN_\***: ulang paket 150, "beli" 100, masak sekali 3 hari, protein 10 per gram | Menentukan rencana makan otomatis. Aturan keselamatan (lantai, alergi, halal, batas santan/goreng) adalah aturan keras yang diuji, bukan bobot | Nilai pertama tanpa sumber, ⚠ sampai ahli gizi atau Anda mengonfirmasi (Rules R_PLAN_*; Review-Planner §5.4; D-37, D-49) | Bobot lain; atau ahli gizi meninjau | Menu berubah, bukan batas keselamatan | Konstanta di `domain/food/Planner.kt`; perbarui tes `plannerGolden_*` |
| 11 | **Target protein dan planner mengincarnya** | Protein 1,6 g/kg (batas 1,4–2,0) bersumber (S6, ✅); yang tanpa sumber hanya **bobot** planner (biaya 10/gram) | Sesuai jawaban Anda 2026-11-19: planner mengincar rentang protein, tetapi hanya di antara kombinasi yang sudah lolos lantai dan filter (D-50, D-53) | Matikan | Protein tidak lagi memengaruhi rencana | Bobot `R_PLAN_PROT_*` = 0 di `Planner.kt` (D-50) |
| 12 | **Perkiraan makanan** (centong nasi 100 g, piring 200 g, 18 perkiraan; 108 baris masakan daerah dari resep sendiri; 2 baris kemasan dari situs pihak ketiga) | Kalori dan takaran adalah perkiraan, ditandai ⚠ | Ditampilkan dengan "±" dan tanda perkiraan (Rules G7, G8; Content §4, §8; D-36) | Timbang di rumah; foto label | Angka makanan lebih akurat | `EST` di `tools/build-household.mjs`; baris di `foods-regional.json` |

### C. Privasi dan izin

| # | Keputusan | Kenapa penting | Pilihan sekarang | Alternatif | Dampak bila diubah | Batal |
| --- | --- | --- | --- | --- | --- | --- |
| 13 | **Teks kebijakan privasi** | Dibutuhkan Play dan Google; harus benar | Versi konservatif dari fakta aplikasi; tanggal berlaku dan kontak masih ⚠ kosong (D-102; Privacy-Policy.md baris 5) | Anda tulis ulang | Harus sama di dua berkas | `strings_privacy.xml` dan `docs/Privacy-Policy.md`; tes `PrivacyTextTest` menjaganya |
| 14 | **Foto kartu: mosaik wajah** | Foto dibagikan ke luar | Memakai `FaceDetector` bawaan Android; bila **tidak ada wajah terdeteksi, seluruh foto dipikselkan**; QR aktif secara bawaan; "Tanpa kg" menyembunyikan semua angka kg (D-74, D-76, D-77) | Pustaka deteksi wajah; QR mati | Lebih akurat tapi ada dependensi baru | `WHOLE_PICTURE_BLOCKS` di kode kartu; `R_SHARE_*` di Rules §9 |
| 15 | **Izin: kamera, notifikasi, alarm tepat** | Izin ditanya saat dibutuhkan saja | Kamera saat "Ambil foto"; notifikasi saat saklar pengingat dinyalakan (Android 13+); alarm istirahat `setExactAndAllowWhileIdle`, bila ditolak timer hanya jalan di aplikasi (D-58, D-83, D-109, D-118). Health Connect: hanya langkah (`READ_STEPS`) | Minta di awal | Izin ditolak lebih sering | Manifest; `AndroidRestAlarms.schedule` |
| 16 | **Hapus semua data** tidak menghapus cadangan Drive; layar mengatakannya | Pengguna harus tahu | Seperti itu (D-107) | Hapus cadangan juga | Butuh izin Drive lebih luas | `BackupService` / layar Data |
| 17 | **Tautan unduh QR dan alamat Masukan** masih kosong/placeholder | QR membawa ke halaman yang belum ada | `SHARE_URL_IS_PLACEHOLDER = true`; `FORM_URL = ""` (D-74, D-103) | Anda siapkan halaman dan formulir | — | `data/share/ShareConfig.kt`; `FeedbackConfig.FORM_URL` |

### D. Hal berbiaya / teknis

| # | Keputusan | Kenapa penting | Pilihan sekarang | Alternatif | Dampak bila diubah | Batal |
| --- | --- | --- | --- | --- | --- | --- |
| 18 | **Target SDK 36 (Android 16) vs 37** | Play mewajibkan target 36+ sejak 2026-08-31. Kenaikan ke 37 diperkirakan sekitar 2027-08-31, **itu perkiraan, bukan tanggal resmi** (Design §1; TODO S-02 `[~]`) | Tetap 36; cek ulang sebelum rilis | Naik ke 37 lebih awal | Perubahan perilaku Android baru, uji ulang | `targetSdk` di `app/build.gradle.kts`; Design §1 |
| 19 | **Lint `OldTargetApi` dimatikan** (juga `PluralsCandidate`) | Tanpa itu CI merah tanpa perubahan kode saat runner dapat platform lebih baru (D-16, D-43; Design, tabel Android Lint) | Dimatikan; target SDK dinaikkan dengan sengaja | Biarkan menyala dan perbaiki saat merah | CI bisa gagal tiba-tiba | Hapus baris di `app/build.gradle.kts` |
| 20 | **Redmi 10 sebagai HP acuan** (pilihan Anda 2026-11-18, TODO S-05) | HP lebih kuat dari rencana (4 GB+, Android 11) | Design §10 menambah emulator API 26 / 2 GB; Redmi 10 tanpa root tidak bisa menurunkan CPU, jadi batasnya dibagi 4 (mulai dingin 500 ms, ganti tab 25 ms) ⚠ (D-129) | HP lebih lemah | — | Hapus `--unthrottled` dari perintah di Speed.md (D-129) |
| 21 | **Kunci publik update konten masih placeholder**; pembaruan konten mati | Aturan 3: kunci tanda tangan milik Anda | `PUBLIC_KEY_IS_PLACEHOLDER = true` (D-24) | — | — | Lihat bagian 2, butir 4 |
| 22 | **Gambar gerak latihan**: hanya 44 dari 149 latihan punya gambar; 105 belum | Tidak menggambar tanpa tabel sudut sendi dari studi (C-08c) | Peta otot tetap tampil; 7 kardio `map-only`; `PUSHV_DB` memakai rig duduk; `overhead-triceps-ext` digambar duduk ⚠ (D-17–20, D-120–124) | Pilih rig berdiri; beri gambar | Ukuran dan akurasi gambar | `content/figures/no-rig.json`, `rig-local.src.json` |
| 23 | **Profil baseline tidak ada** | Sandbox tanpa KVM; berkas tak boleh ditulis tangan | Hanya generator yang di-commit (D-132) | Jalankan di HP | Mulai dingin lebih cepat | `:app:generateBaselineProfile` di perangkat API 33+ |

---

## 2. Yang menunggu Anda

Hanya tindakan Anda; Claude tidak boleh mengerjakannya atau berpura-pura (Decisions.md, aturan 3). **Urutan yang disarankan** dan perkiraan waktu (perkiraan kasar saya; dokumen sumber tidak memberi waktu kecuali disebut):

| Urut | Tindakan | Perkiraan | Catatan dan lokasi |
| --- | --- | --- | --- |
| 1 | **Uji di HP** (Redmi 10 dan emulator API 26) | 1–2 jam | 22 langkah di [Test-Manual.md](Test-Manual.md) (K-1…K-8, A, N, C, S-1…S-6) lalu `StartupBenchmark` di [Speed.md](Speed.md). Belum ada yang berjalan di perangkat (lihat bagian 3). Lakukan **dulu**: hasilnya bisa mengubah butir lain. |
| 2 | **Kunci tanda tangan update konten + repo `xnk-fit-content`** | 30–45 menit | [Content-Update.md §7](Content-Update.md): buat kunci pribadi di komputer Anda (offline, jangan di repo atau chat), tempel kunci publik di `ContentConfig.kt`, set `PUBLIC_KEY_IS_PLACEHOLDER = false`, buat repo + GitHub Pages. Tanpa ini, pembaruan konten tetap mati. |
| 3 | **ID klien OAuth Drive** | 30–60 menit | [Google-OAuth.md §2](Google-OAuth.md): proyek Cloud, aktifkan Drive API, layar persetujuan, scope `drive.appdata` saja, klien Android; daftarkan **semua** SHA-1 (termasuk kunci Play App Signing, yang baru ada setelah akun developer). Teruskan ke build sebagai rahasia CI (`DRIVE_OAUTH_CLIENT_ID`, D-90). Tanpa ini, cadangan Drive tampil "belum tersedia". |
| 4 | **Teks privasi** | 15–30 menit | Isi tanggal berlaku dan kontak di `strings_privacy.xml` dan `docs/Privacy-Policy.md`, hapus tanda ⚠ (D-102). Dibutuhkan sebelum langkah 6. |
| 5 | **C-09: tinjau teks cue bahasa Inggris** | Tidak ada perkiraan di dokumen; ada 149 latihan | TODO C-09 `[ ]`. |
| 6 | **Akun developer Play (S-08)** | Tidak ada perkiraan di dokumen | Anda sendiri menentukan ini yang terakhir (TODO S-08). Setelahnya: formulir Health apps + Data safety ([Play-Health.md §3](Play-Health.md); waktu peninjauan tidak dipublikasikan, ajukan saat build tes pertama diunggah), lalu B-20 → uji tertutup R-01 (12 penguji, 14 hari). |
| 7 | **Foto label BPOM / kemasan** | Lepas dari urutan; kapan saja | Hanya 2 dari target sekitar 80 baris kemasan ada, keduanya dari pihak ketiga ⚠ (TODO C-06; Content §4.3 mencatat 13 produk gagal). Dibutuhkan foto label atau tabel produsen. |

Opsional menurut dokumen: mendaftarkan domain `xnkfit.com` (TODO S-01, PDKI belum bisa dicek), halaman unduh untuk QR (butir 17), alamat formulir Masukan.

---

## 3. Apa yang belum teruji

Seluruh kode lulus tes hanya di JVM (Robolectric dan tes unit; sebagian dijalankan CI), dan **belum pernah dijalankan di perangkat nyata atau emulator**: tidak ada tes UI Compose (D-33, D-48, D-68), tidak ada uji kamera, alarm istirahat, Doze, notifikasi di MIUI, mematikan proses, tombol kembali, TalkBack, tampilan tablet dan mode gelap secara nyata (hanya diperiksa lewat tes semantik, D-127; sisanya langkah manual di Test-Manual.md). Kecepatan mulai dingin dan ganti tab belum diukur di HP; angka di Speed.md untuk set-tick dan pencarian makanan berasal dari SQLite Robolectric di CI, bukan dari ponsel. Masuk Google dan cadangan Drive dengan server asli tidak pernah dicoba (hanya server Drive palsu di tes, TODO B-16), pembaruan konten bertanda tangan belum pernah dijalankan dengan kunci asli, dan pemeriksaan pembaruan otomatis saat buka aplikasi belum dipasang (D-106). Kunci pengaman (mis. jangan ada defisit untuk di bawah 18) diuji sebagai aturan di JVM, tetapi alur layarnya belum dilihat oleh manusia di perangkat. Gradle lokal sempat diblokir Maven Central 429 pada B-15 sehingga tesnya dicek lewat CI (D-119).

---

## Lampiran A. ⚠ yang masih terbuka

| ⚠ | Lokasi | Keadaan |
| --- | --- | --- |
| R_LEVEL | Rules R_LEVEL, §12 #3; D-54 | Tuning, tes di-`@Ignore` |
| R_TRN_RETURN (60 %) | Rules R_TRN_RETURN, §10 G9 | Tanpa sumber |
| R_PLAN_REPEAT 150, R_PLAN_BUY 100, R_PLAN_COOK_ONCE 3 hari, R_PLAN_PROT 10/gram, R_BUDGET 300/20 | Rules R_PLAN_*, R_BUDGET; Review-Planner §5.4 | Nilai pertama tanpa sumber |
| G7 centong, piring, mangkok, masakan goreng/matang | Rules §10 G7; Content §4.6 (18 perkiraan) | Belum ditimbang |
| G8 makanan beli dan masakan daerah | Rules §10 G8; Content §4.4, §8 (107 dari 108 baris ⚠) | Perkiraan bertanda; 0 baris `bought` resmi |
| C-06 kemasan | Content §4.3, §8 | 2 baris pihak ketiga, tanpa label |
| C-01b otot: 34 latihan `uncertain`, 27 `unverified` | Content §8, §12.3 | Butuh studi atau keputusan Anda |
| C-08c: 105 latihan tanpa rig; `seated-leg-curl` 10–100° vs Maeo 2021 | Content §8, §5.4 | Butuh tabel sudut sendi |
| `PUSHV_DB` rig duduk; `overhead-triceps-ext` digambar duduk | Content (baris 248); D-120, D-124 | Pilihan Claude, ⚠ |
| Estimasi "segar/mentah" TKPI untuk sayur rebus tanpa baris "rebus" | Content §4 (baris 140) | Asumsi ⚠ |
| Tingkat anggaran paket (`foodTier`) | Content §8 C-11 | Penilaian, bukan harga |
| Batas Redmi 10 dibagi 4 (500 ms / 25 ms) | Speed.md; D-129 | Dibaca lebih ketat |
| Tanggal berlaku dan kontak kebijakan privasi | Privacy-Policy.md baris 5; D-102 | Anda isi |
| Play Health: teks "bukan nasihat medis" sesuai kebijakan terbaru; pengungkapan tambahan untuk langkah | Play-Health.md §5 | Cek ulang sebelum R-02 |
| OAuth: perilaku aplikasi berstatus Testing (batas penguji, kedaluwarsa token 7 hari) | Google-OAuth.md §5 | Belum dipastikan |
| Target SDK 37: tanggal tidak resmi | Design §1 | Cek ulang sebelum rilis |

## Lampiran B. Keputusan → baris di Decisions.md

Nomor baris adalah nomor baris berkas Decisions.md saat ini.

| Butir di atas | Baris Decisions.md |
| --- | --- |
| 1 Batas usia | 21 (B-02), 31 (B-05) |
| 2 Hamil | 29 (B-05); Rules R_GATE_PREG, §12 #8 |
| 3 Gangguan makan | Rules R_GATE_ED, G4 (tidak ada baris Decisions) |
| 4 Lantai kalori | 22 (B-02, pembulatan lantai ke atas) |
| 5 Beban setelah libur | 62 (B-06); Rules G9 |
| 6 Skrining | 29, 30 (B-05) |
| 7 Batas defisit | 22 (B-02) |
| 8 R_LEVEL | 54 (B-12) |
| 9 Anggaran | 37, 49 (B-10); Content C-11 |
| 10 Bobot planner | 37, 49, 50, 53 (B-10) |
| 11 Protein | 50, 53 (B-10 owner) |
| 12 Perkiraan makanan | 34–36 (C-05), 25 (B-04); Rules G7, G8 |
| 13 Privasi | 102 (B-14) |
| 14 Foto kartu | 74, 76, 77 (B-13) |
| 15 Izin | 58 (B-12), 83 (B-07), 109 (B-14), 118 (B-15) |
| 16 Hapus data | 107 (B-14) |
| 17 QR dan Masukan | 74 (B-13), 103 (B-14) |
| 18 Target SDK | Design §1; TODO S-02 (tidak ada baris Decisions) |
| 19 Lint | 16 (B-01), 43 (B-10) |
| 20 Redmi 10 | 129 (B-19); TODO S-05 |
| 21 Kunci publik | 24, 27 (B-04) |
| 22 Gambar gerak | 17–20 (C-08), 120–124 (C-08b) |
| 23 Profil baseline | 132 (B-19) |
