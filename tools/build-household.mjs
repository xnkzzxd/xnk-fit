#!/usr/bin/env node
// Builds content/household.json (task C-05). Usage: node tools/build-household.mjs
// Household measures (takaran rumah tangga, URT) per food id.
//  - source "pmk-41-2014": copied as printed from the URT tables in the Lampiran of
//    Permenkes RI No. 41 Tahun 2014 (Pedoman Gizi Seimbang), copy from JDIH Kemenkes
//    (URL and sha256 below). Page = page number printed in the PDF (= PDF page).
//  - source "estimate": our own judgement, flagged ⚠, with the method written beside it.
// Nothing here edits foods-*.json; the app joins this file to food rows by foodId (B-04).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(here, '..');
const read = f => JSON.parse(fs.readFileSync(path.join(root, 'content', f), 'utf8'));
const tkpi = new Map(read('foods-tkpi.json').foods.map(f => [f.id, f]));
const est = new Map(read('foods-estimates.json').foods.map(f => [f.id, f]));
const nameOf = id => (tkpi.get(id) || est.get(id) || {}).name_id;
const fileOf = id => tkpi.has(id) ? 'foods-tkpi.json' : est.has(id) ? 'foods-estimates.json' : null;

// Unit codes (Content §4.1 list plus the units the PMK tables print).
const UNITS = {
  centong: 'centong nasi (sendok nasi)', piring: 'piring', mangkok: 'mangkok', gelas: 'gelas',
  sdm: 'sendok makan', sdt: 'sendok teh', potong: 'potong', iris: 'iris', butir: 'butir',
  buah: 'buah', biji: 'biji', ekor: 'ekor', lembar: 'lembar', papan: 'papan (petai)', genggam: 'genggam', bungkus: 'bungkus (pepes, nasi bungkus)',
};

// ---- PMK 41/2014 URT tables, as printed -------------------------------------------------
// [id, page, table, printed name, printed URT, qty, unit, grams, mapTo[], note]
// qty is the printed count; grams is the printed weight for that whole count.
// gramsRange where the PMK prints a range (grams then = middle, marked derived).
const T_NASI = 'Penukar 1 porsi nasi (sumber karbohidrat)';
const T_NAB = 'Penukar 1 porsi tempe (protein nabati)';
const T_HEW = 'Penukar 1 porsi ikan segar (protein hewani)';
const T_HEW2 = 'Pangan lain penukar 1 porsi ikan segar';
const T_A = 'Protein hewani golongan A (rendah lemak)';
const T_B = 'Protein hewani golongan B (lemak sedang)';
const T_C = 'Protein hewani golongan C (tinggi lemak)';
const T_BUAH = 'Penukar 1 porsi buah (berat bersih tanpa kulit dan biji)';
const T_GULA = 'Penukar 1 porsi gula';
const T_LTJ = 'Penukar 1 porsi minyak (lemak tak jenuh)';
const T_LJ = 'Penukar lemak jenuh';
const T_KET = 'Keterangan porsi (contoh menu kelompok umur)';
const T_SAYUR = 'Kelompok sayuran golongan B dan C';

const PMK = [
  // page 8-9: nasi
  ['nasi-porsi', 8, T_NASI, 'Nasi (porsi acuan)', '¾ gelas', 0.75, 'gelas', 100, [], 'Teks hal. 8: 1 porsi nasi ±100 g setara ¾ gelas.'],
  ['bihun', 9, T_NASI, 'Bihun', '½ gelas', 0.5, 'gelas', 50, ['TKPI_AP006']],
  ['biskuit', 9, T_NASI, 'Biskuit', '4 buah besar', 4, 'buah', 40, ['TKPI_AP029']],
  ['havermut', 9, T_NASI, 'Havermut', '5 ½ sendok besar', 5.5, 'sdm', 45, [], 'Tidak ada baris havermut di TKPI 2020.'],
  ['jagung-segar', 9, T_NASI, 'Jagung segar', '3 buah sedang', 3, 'buah', 125, ['TKPI_AR015'], 'PMK tidak menyebut berat bersih atau dengan tongkol.'],
  ['kentang', 9, T_NASI, 'Kentang', '2 buah sedang', 2, 'buah', 210, ['TKPI_BR013']],
  ['kentang-hitam', 9, T_NASI, 'Kentang hitam', '12 biji', 12, 'biji', 125, ['TKPI_BR014']],
  ['maizena', 9, T_NASI, 'Maizena', '10 sendok makan', 10, 'sdm', 50, ['TKPI_AP019']],
  ['makaroni', 9, T_NASI, 'Makaroni', '½ gelas', 0.5, 'gelas', 50, ['TKPI_AP020']],
  ['mie-basah', 9, T_NASI, 'Mie basah', '2 gelas', 2, 'gelas', 200, ['TKPI_AP021']],
  ['mie-kering', 9, T_NASI, 'Mie kering', '1 gelas', 1, 'gelas', 50, ['TKPI_AP022']],
  ['nasi-putih', 9, T_NASI, 'Nasi beras giling putih', '¾ gelas', 0.75, 'gelas', 100, ['TKPI_AP001', 'W_NASI_PUTIH']],
  ['nasi-merah', 9, T_NASI, 'Nasi beras giling merah', '¾ gelas', 0.75, 'gelas', 100, ['TKPI_AP005']],
  ['nasi-hitam', 9, T_NASI, 'Nasi beras giling hitam', '¾ gelas', 0.75, 'gelas', 100, [], 'Tidak ada baris nasi beras hitam di TKPI 2020.'],
  ['nasi-setengah-giling', 9, T_NASI, 'Nasi beras ½ giling', '¾ gelas', 0.75, 'gelas', 100, [], 'Tidak ada baris yang sama di TKPI 2020.'],
  ['nasi-ketan-putih', 9, T_NASI, 'Nasi ketan putih', '¾ gelas', 0.75, 'gelas', 100, ['TKPI_AP017']],
  ['roti-putih', 9, T_NASI, 'Roti putih', '3 iris', 3, 'iris', 70, ['TKPI_AP024']],
  ['roti-coklat', 9, T_NASI, 'Roti warna coklat', '3 iris', 3, 'iris', 70, ['TKPI_AP104'], 'Dipetakan ke "Roti warna sawo matang".'],
  ['singkong', 9, T_NASI, 'Singkong', '1 ½ potong', 1.5, 'potong', 120, ['TKPI_BR016']],
  ['sukun', 9, T_NASI, 'Sukun', '3 potong sedang', 3, 'potong', 150, ['TKPI_ER110'], 'PMK tidak menyebut muda atau tua; dipetakan ke sukun tua.'],
  ['talas', 9, T_NASI, 'Talas', '½ biji sedang', 0.5, 'biji', 125, ['TKPI_BR025'], 'PMK tidak menyebut jenis; dipetakan ke talas bogor.'],
  ['tape-ketan', 9, T_NASI, 'Tape beras ketan', '5 sendok makan', 5, 'sdm', 100, [], 'Tidak ada baris tape ketan di TKPI 2020.'],
  ['tape-singkong', 9, T_NASI, 'Tape singkong', '1 potong sedang', 1, 'potong', 100, ['TKPI_BP063']],
  ['tepung-tapioka', 9, T_NASI, 'Tepung tapioca', '8 sendok makan', 8, 'sdm', 50, ['TKPI_BP070']],
  ['tepung-beras', 9, T_NASI, 'Tepung beras', '8 sendok makan', 8, 'sdm', 50, ['TKPI_AP004']],
  ['tepung-hunkwe', 9, T_NASI, 'Tepung hunkwe', '10 sendok makan', 10, 'sdm', 50, ['TKPI_CP084']],
  ['tepung-sagu', 9, T_NASI, 'Tepung sagu', '8 sendok makan', 8, 'sdm', 50, [], 'TKPI punya beberapa jenis sagu; tidak dipilih.'],
  ['tepung-singkong', 9, T_NASI, 'Tepung singkong', '5 sendok makan', 5, 'sdm', 50, [], 'TKPI BP070 menggabung singkong/tapioka; PMK memberi 8 sdm (tapioka) dan 5 sdm (singkong). Hanya tapioka dipasang.'],
  ['tepung-terigu', 9, T_NASI, 'Tepung terigu', '5 sendok makan', 5, 'sdm', 50, ['TKPI_AP025']],
  ['ubi-jalar-kuning', 9, T_NASI, 'Ubi jalar kuning', '1 biji sedang', 1, 'biji', 135, ['TKPI_BR028']],
  ['kerupuk', 9, T_NASI, 'Kerupuk udang/ikan', '3 biji sedang', 3, 'biji', 30, ['TKPI_BP045'], 'PMK tidak menyebut mentah atau goreng; dipetakan ke kerupuk udang goreng.'],
  // page 10: protein nabati
  ['tempe', 10, T_NAB, 'Tempe (porsi acuan)', '2 potong sedang', 2, 'potong', 50, ['TKPI_CP077', 'TKPI_CP082'], 'Teks hal. 10: 1 porsi tempe 2 potong sedang atau 50 g.'],
  ['kacang-hijau', 10, T_NAB, 'Kacang hijau', '2 ½ sendok makan', 2.5, 'sdm', 25, ['TKPI_CR014']],
  ['kacang-kedelai', 10, T_NAB, 'Kacang kedelai', '2 ½ sendok makan', 2.5, 'sdm', 25, ['TKPI_CR017']],
  ['kacang-merah', 10, T_NAB, 'Kacang merah', '2 ½ sendok makan', 2.5, 'sdm', 25, ['TKPI_CR026']],
  ['kacang-mete', 10, T_NAB, 'Kacang mete', '1 ½ sendok makan', 1.5, 'sdm', 15, ['TKPI_CR028']],
  ['kacang-tanah', 10, T_NAB, 'Kacang tanah kupas', '2 sendok makan', 2, 'sdm', 20, ['TKPI_CR032']],
  ['kacang-tolo', 10, T_NAB, 'Kacang toto (tolo)', '2 sendok makan', 2, 'sdm', 20, ['TKPI_CR033'], 'Dicetak "Kacang Toto"; dibaca sebagai kacang tolo.'],
  ['selai-kacang', 10, T_NAB, 'Keju kacang tanah', '1 sendok makan', 1, 'sdm', 15, ['TKPI_CP036']],
  ['kembang-tahu', 10, T_NAB, 'Kembang tahu', '1 lembar', 1, 'lembar', 20, ['TKPI_CP037']],
  ['oncom', 10, T_NAB, 'Oncom', '2 potong besar', 2, 'potong', 50, ['TKPI_CP051']],
  ['petai', 10, T_NAB, 'Petai segar', '1 papan/biji besar', 1, 'papan', 20, ['TKPI_DR134']],
  ['tahu', 10, T_NAB, 'Tahu', '2 potong sedang', 2, 'potong', 100, ['TKPI_CP061']],
  ['sari-kedelai', 10, T_NAB, 'Sari kedelai', '2 ½ gelas', 2.5, 'gelas', 185, [], 'Tidak dipasang: 74 g per gelas tidak cocok untuk cairan (lihat suspect).', 'per-gelas-cairan'],
  // page 11: protein hewani
  ['daging-sapi', 11, T_HEW, 'Daging sapi', '1 potong sedang', 1, 'potong', 35, ['TKPI_FR026'], 'PMK tidak menyebut kadar lemak; dipetakan ke daging sapi lemak sedang.'],
  ['daging-ayam', 11, T_HEW, 'Daging ayam', '1 potong sedang', 1, 'potong', 40, ['TKPI_FR005']],
  ['hati-sapi', 11, T_HEW, 'Hati sapi', '1 potong sedang', 1, 'potong', 50, ['TKPI_FR031']],
  ['ikan-asin', 11, T_HEW, 'Ikan asin', '1 potong kecil', 1, 'potong', 15, ['TKPI_GP004'], 'Hal. 12 mencetak "ikan asin kering 1 potong sedang 15 g"; berat sama.'],
  ['teri-kering', 11, T_HEW, 'Ikan teri kering', '1 sendok makan', 1, 'sdm', 20, ['TKPI_GP042']],
  ['telur-ayam', 11, T_HEW, 'Telur ayam', '1 butir', 1, 'butir', 55, ['TKPI_HR002']],
  ['udang-basah', 11, T_HEW, 'Udang basah', '5 ekor sedang', 5, 'ekor', 35, ['TKPI_GR084'], 'Hal. 12 mencetak "udang segar 5 ekor sedang 35 g"; sama.'],
  ['ikan-segar', 11, T_HEW, 'Ikan segar (porsi acuan)', '1 potong sedang', 1, 'potong', 40, [], 'Acuan umum, tidak dipasang ke satu jenis ikan. Hal. 89 mencetak "ikan segar 1/3 ekor = 45 g" (konflik).'],
  ['susu-sapi', 11, T_HEW2, 'Susu sapi', '1 gelas', 1, 'gelas', 200, ['TKPI_JR006']],
  ['susu-kerbau', 11, T_HEW2, 'Susu kerbau', '½ gelas', 0.5, 'gelas', 100, ['TKPI_JR004']],
  ['susu-kambing', 11, T_HEW2, 'Susu kambing', '¾ gelas', 0.75, 'gelas', 185, ['TKPI_JR003'], '247 g per gelas, lebih berat dari susu sapi 200 g per gelas; dicetak begitu.'],
  ['tepung-sari-kedele', 11, T_HEW2, 'Tepung sari kedele', '3 sendok makan', 3, 'sdm', 20, [], 'Tidak ada baris yang sama di TKPI 2020.'],
  ['tepung-susu-whole', 11, T_HEW2, 'Tepung susu whole', '4 sendok makan', 4, 'sdm', 20, ['TKPI_JP006']],
  ['tepung-susu-krim', 11, T_HEW2, 'Tepung susu krim', '4 sendok makan', 4, 'sdm', 20, [], 'Arti "krim" tidak jelas (skim atau krim); tidak dipasang.'],
  // page 12: golongan A
  ['babat', 12, T_A, 'Babat', '1 potong sedang', 1, 'potong', 40, ['TKPI_FR023']],
  ['cumi', 12, T_A, 'Cumi-cumi', '1 ekor kecil', 1, 'ekor', 45, ['TKPI_GR003']],
  ['daging-asap', 12, T_A, 'Daging asap', '1 lembar', 1, 'lembar', 20, [], 'Tidak ada baris yang sama di TKPI 2020.'],
  ['daging-kerbau', 12, T_A, 'Daging kerbau', '1 potong sedang', 1, 'potong', 35, ['TKPI_FR021']],
  ['dendeng-sapi', 12, T_A, 'Dendeng sapi', '1 potong sedang', 1, 'potong', 15, ['TKPI_FP014']],
  ['gabus-kering', 12, T_A, 'Gabus kering', '1 ekor kecil', 1, 'ekor', 10, ['TKPI_GP012']],
  ['ikan-kakap', 12, T_A, 'Ikan kakap', '1/3 ekor besar', 1 / 3, 'ekor', 35, ['TKPI_GR030']],
  ['ikan-kembung', 12, T_A, 'Ikan kembung', '1/3 ekor sedang', 1 / 3, 'ekor', 30, ['TKPI_GR050']],
  ['ikan-lele', 12, T_A, 'Ikan lele', '1/3 ekor sedang', 1 / 3, 'ekor', 40, [], 'Tidak ada baris lele segar di TKPI 2020.'],
  ['ikan-mas', 12, T_A, 'Ikan mas', '1/3 ekor sedang', 1 / 3, 'ekor', 45, ['TKPI_GR046']],
  ['ikan-mujair', 12, T_A, 'Ikan mujair', '1/3 ekor sedang', 1 / 3, 'ekor', 30, ['TKPI_GR048']],
  ['ikan-peda', 12, T_A, 'Ikan peda', '1 ekor kecil', 1, 'ekor', 35, ['TKPI_GP010']],
  ['ikan-pindang', 12, T_A, 'Ikan pindang', '½ ekor sedang', 0.5, 'ekor', 25, [], 'TKPI punya 4 jenis pindang; tidak dipilih.'],
  ['ikan-cakalang-asin', 12, T_A, 'Ikan cakalang asin', '1 potong sedang', 1, 'potong', 20, ['TKPI_GP009']],
  ['kerang', 12, T_A, 'Kerang', '½ gelas', 0.5, 'gelas', 90, ['TKPI_GR074']],
  ['ikan-lemuru', 12, T_A, 'Ikan lemuru', '1 potong sedang', 1, 'potong', 35, ['TKPI_GR042']],
  ['putih-telur', 12, T_A, 'Putih telur ayam', '2 ½ butir', 2.5, 'butir', 65, ['TKPI_HR004']],
  ['rebon-kering', 12, T_A, 'Rebon kering', '2 sendok makan', 2, 'sdm', 10, ['TKPI_GP047']],
  ['rebon-basah', 12, T_A, 'Rebon basah', '2 sendok makan', 2, 'sdm', 45, ['TKPI_GR080']],
  ['selar-kering', 12, T_A, 'Selar kering', '1 ekor', 1, 'ekor', 20, ['TKPI_GP036']],
  ['sepat-kering', 12, T_A, 'Sepat kering', '1 potong sedang', 1, 'potong', 20, ['TKPI_GP037']],
  ['teri-nasi', 12, T_A, 'Teri nasi', '1/3 gelas', 1 / 3, 'gelas', 20, ['TKPI_GP040']],
  // page 13: golongan B and C
  ['bakso', 13, T_B, 'Bakso', '10 biji sedang', 10, 'biji', 170, [], 'Tidak ada baris bakso di TKPI 2020.'],
  ['daging-kambing', 13, T_B, 'Daging kambing', '1 potong sedang', 1, 'potong', 40, ['TKPI_FR019']],
  ['ginjal-sapi', 13, T_B, 'Ginjal sapi', '1 potong besar', 1, 'potong', 45, ['TKPI_FR029']],
  ['hati-ayam', 13, T_B, 'Hati ayam', '1 buah sedang', 1, 'buah', 30, ['TKPI_FR007']],
  ['otak', 13, T_B, 'Otak', '1 potong besar', 1, 'potong', 65, ['TKPI_FR032']],
  ['telur-bebek-asin', 13, T_B, 'Telur bebek asin', '1 butir', 1, 'butir', 50, ['TKPI_HP002']],
  ['telur-puyuh', 13, T_B, 'Telur puyuh', '5 butir', 5, 'butir', 55, ['TKPI_HR011']],
  ['usus-sapi', 13, T_B, 'Usus sapi', '1 potong besar', 1, 'potong', 50, ['TKPI_FR033']],
  ['bebek', 13, T_C, 'Bebek', '1 potong sedang', 1, 'potong', 45, ['TKPI_FR012']],
  ['belut', 13, T_C, 'Belut', '3 ekor', 3, 'ekor', 45, ['TKPI_GR001']],
  ['kornet', 13, T_C, 'Kornet daging sapi', '3 sendok makan', 3, 'sdm', 45, ['TKPI_FP015']],
  ['ayam-kulit', 13, T_C, 'Ayam dengan kulit', '1 potong sedang', 1, 'potong', 40, [], 'TKPI FR005 tidak menyebut kulit; FR005 memakai baris daging ayam hal. 11 (berat sama).'],
  ['ham', 13, T_C, 'Ham', '1 ½ potong kecil', 1.5, 'potong', 40, [], 'Tidak dipasang: ham umumnya babi (R_HALAL).'],
  ['sardencis', 13, T_C, 'Sardencis', '½ potong', 0.5, 'potong', 35, [], 'Sarden kaleng; TKPI GR057 sarden segar bukan bahan yang sama.'],
  ['sosis', 13, T_C, 'Sosis', '½ potong', 0.5, 'potong', 50, [], 'Jenis sosis tidak disebut; tidak dipasang.'],
  ['kuning-telur', 13, T_C, 'Kuning telur ayam', '4 butir', 4, 'butir', 45, ['TKPI_HR003']],
  ['telur-bebek', 13, T_C, 'Telur bebek', '1 butir', 1, 'butir', 55, ['TKPI_HR008'], 'Dipetakan ke telur bebek tambak (TKPI tidak punya "telur bebek" umum).'],
  // Daging babi (hal. 13) tidak disalin: R_HALAL, tidak ada di aplikasi.
  // page 15-16: buah (berat bersih)
  ['alpukat', 15, T_BUAH, 'Alpokat', '½ buah besar', 0.5, 'buah', 50, ['TKPI_ER001'], 'Hal. 41 mencetak alpokat ½ buah besar = 60 g (konflik); dipakai 50 g dari tabel buah.'],
  ['anggur', 15, T_BUAH, 'Anggur', '20 buah sedang', 20, 'buah', 165, [], 'TKPI hanya punya anggur hutan; tidak dipasang.'],
  ['apel-merah', 15, T_BUAH, 'Apel merah', '1 buah kecil', 1, 'buah', 85, ['TKPI_ER004']],
  ['apel-malang', 15, T_BUAH, 'Apel malang', '1 buah sedang', 1, 'buah', 75, ['TKPI_ER003']],
  ['belimbing', 15, T_BUAH, 'Belimbing', '1 buah besar', 1, 'buah', [125, 140], ['TKPI_ER006']],
  ['blewah', 15, T_BUAH, 'Blewah', '1 potong sedang', 1, 'potong', 70, [], 'Tidak ada baris blewah di TKPI 2020.'],
  ['duku', 15, T_BUAH, 'Duku', '10-16 buah sedang', 13, 'buah', 80, ['TKPI_ER022'], 'Jumlah dicetak 10-16; qty 13 adalah tengah (turunan).'],
  ['durian', 15, T_BUAH, 'Durian', '2 biji besar', 2, 'biji', 35, ['TKPI_ER023']],
  ['jambu-air', 15, T_BUAH, 'Jambu air', '2 buah sedang', 2, 'buah', 100, ['TKPI_ER030']],
  ['jambu-biji', 15, T_BUAH, 'Jambu biji', '1 buah besar', 1, 'buah', 100, ['TKPI_ER031']],
  ['jambu-bol', 15, T_BUAH, 'Jambu bol', '1 buah kecil', 1, 'buah', 90, ['TKPI_ER033']],
  ['jeruk-bali', 15, T_BUAH, 'Jeruk bali', '1 potong', 1, 'potong', 105, ['TKPI_ER035']],
  ['jeruk-garut', 15, T_BUAH, 'Jeruk garut', '1 buah sedang', 1, 'buah', 115, ['TKPI_ER037']],
  ['jeruk-manis', 15, T_BUAH, 'Jeruk manis', '2 buah sedang', 2, 'buah', 100, ['TKPI_ER039']],
  ['jeruk-nipis', 15, T_BUAH, 'Jeruk nipis', '1 ¼ gelas', 1.25, 'gelas', 135, [], 'Tidak dipasang: 108 g per gelas air jeruk tidak cocok untuk cairan (lihat suspect).', 'per-gelas-cairan'],
  ['kedondong-sedang', 15, T_BUAH, 'Kedondong', '2 buah sedang', 2, 'buah', 100, ['TKPI_ER044'], 'Dicetak "2 buah sedang/besar 100/120".'],
  ['kedondong-besar', 15, T_BUAH, 'Kedondong', '2 buah besar', 2, 'buah', 120, ['TKPI_ER044'], 'Dicetak "2 buah sedang/besar 100/120".'],
  ['kesemek', 15, T_BUAH, 'Kesemek', '½ buah', 0.5, 'buah', 65, ['TKPI_ER048']],
  ['kurma', 15, T_BUAH, 'Kurma', '3 buah', 3, 'buah', 15, [], 'Tidak ada baris kurma di TKPI 2020.'],
  ['leci', 15, T_BUAH, 'Leci', '10 buah', 10, 'buah', 75, [], 'Tidak ada baris leci di TKPI 2020.'],
  ['mangga', 15, T_BUAH, 'Mangga', '¾ buah besar', 0.75, 'buah', 90, ['TKPI_ER054']],
  ['manggis', 15, T_BUAH, 'Manggis', '2 buah sedang', 2, 'buah', 80, ['TKPI_ER064']],
  ['markisa', 16, T_BUAH, 'Markisa', '¾ buah sedang', 0.75, 'buah', 35, ['TKPI_ER065']],
  ['melon', 16, T_BUAH, 'Melon', '1 potong', 1, 'potong', 90, ['TKPI_ER067']],
  ['nangka', 16, T_BUAH, 'Nangka masak', '3 biji sedang', 3, 'biji', 50, ['TKPI_ER071']],
  ['nanas', 16, T_BUAH, 'Nenas', '¼ buah sedang', 0.25, 'buah', 85, ['TKPI_ER070']],
  ['pear', 16, T_BUAH, 'Pear', '½ buah sedang', 0.5, 'buah', 85, [], 'Tidak ada baris pir di TKPI 2020.'],
  ['pepaya', 16, T_BUAH, 'Pepaya', '1 potong besar', 1, 'potong', [100, 190], ['TKPI_ER073']],
  ['pisang-ambon', 16, T_BUAH, 'Pisang ambon', '1 buah sedang', 1, 'buah', 50, ['TKPI_ER074']],
  ['pisang-kepok', 16, T_BUAH, 'Pisang kepok', '1 buah', 1, 'buah', 45, ['TKPI_ER081']],
  ['pisang-mas', 16, T_BUAH, 'Pisang mas', '2 buah', 2, 'buah', 40, ['TKPI_ER087']],
  ['pisang-raja', 16, T_BUAH, 'Pisang raja', '2 buah kecil', 2, 'buah', 40, ['TKPI_ER089']],
  ['rambutan', 16, T_BUAH, 'Rambutan', '8 buah', 8, 'buah', 75, ['TKPI_ER097']],
  ['sawo', 16, T_BUAH, 'Sawo', '1 buah sedang', 1, 'buah', 50, ['TKPI_ER104'], 'Dipetakan ke sawo manila.'],
  ['salak', 16, T_BUAH, 'Salak', '2 buah sedang', 2, 'buah', 65, [], 'TKPI punya 4 jenis salak (ER101 masih cek tangan); tidak dipilih.'],
  ['semangka', 16, T_BUAH, 'Semangka', '2 potong sedang', 2, 'potong', 180, ['TKPI_ER105']],
  ['sirsak', 16, T_BUAH, 'Sirsak', '½ gelas', 0.5, 'gelas', 60, ['TKPI_ER106']],
  ['srikaya', 16, T_BUAH, 'Srikaya', '2 buah besar', 2, 'buah', 50, ['TKPI_ER108']],
  ['strawberry', 16, T_BUAH, 'Strawberry', '4 buah besar', 4, 'buah', 215, [], 'Tidak ada di TKPI 2020; 54 g per buah juga besar untuk stroberi.'],
  // page 37: gula
  ['gula-pasir', 37, T_GULA, 'Gula tebu (pasir) (porsi acuan)', '1 sendok makan', 1, 'sdm', 10, ['TKPI_MP007'], 'Teks hal. 37. Hal. 89 mencetak "gula 1 sdm = 20 g" (konflik); dipakai 10 g dari teks tabel.'],
  ['gula-aren', 37, T_GULA, 'Gula aren', '1 sendok makan', 1, 'sdm', 10, ['TKPI_MP005']],
  ['gula-kelapa', 37, T_GULA, 'Gula kelapa', '1 sendok makan', 1, 'sdm', 10, ['TKPI_MP006']],
  ['selai', 37, T_GULA, 'Selai/jam', '1 sendok makan', 1, 'sdm', 15, ['TKPI_MP008']],
  ['madu', 37, T_GULA, 'Madu', '1 sendok makan', 1, 'sdm', 15, ['TKPI_MP010']],
  ['sirup', 37, T_GULA, 'Sirup', '1 sendok makan', 1, 'sdm', 15, ['TKPI_MP014']],
  // page 41: minyak
  ['alpukat-lemak', 41, T_LTJ, 'Alpokat', '½ buah besar', 0.5, 'buah', 60, [], 'Konflik dengan hal. 15 (50 g); tidak dipasang.'],
  ['margarin', 41, T_LTJ, 'Margarin jagung', '¼ sendok teh', 0.25, 'sdt', 5, [], 'Tidak dipasang: 20 g per sdt tidak mungkin (lihat suspect).', 'per-sdt'],
  ['mayones', 41, T_LTJ, 'Mayonaise', '2 sendok makan', 2, 'sdm', 25, [], 'Tidak ada baris mayones di TKPI 2020.'],
  ['minyak-kedelai', 41, T_LTJ, 'Minyak kedelai', '1 sendok teh', 1, 'sdt', 5, ['TKPI_KR010']],
  ['minyak-kacang', 41, T_LTJ, 'Minyak kacang tanah', '1 sendok teh', 1, 'sdt', 5, ['TKPI_KR009']],
  ['minyak-zaitun', 41, T_LTJ, 'Minyak zaitun', '1 sendok teh', 1, 'sdt', 5, ['TKPI_KR014']],
  ['minyak-lain', 41, T_LTJ, 'Minyak biji kapas, matahari, jagung, safflower', '1 sendok teh', 1, 'sdt', 5, [], 'Tidak ada baris minyak ini di TKPI 2020.'],
  ['mentega', 41, T_LJ, 'Mentega', '1 sendok makan', 1, 'sdm', 15, ['TKPI_KP002']],
  ['santan-peras', 41, T_LJ, 'Santan (peras)', '1/3 gelas', 1 / 3, 'gelas', 40, ['TKPI_KP004'], 'Dipetakan ke "Santan murni". 120 g per gelas, ringan untuk cairan; dicetak begitu.'],
  ['kelapa', 41, T_LJ, 'Kelapa', '1 potong kecil', 1, 'potong', 15, ['TKPI_KR001'], 'Dipetakan ke kelapa setengah tua.'],
  ['keju-krim', 41, T_LJ, 'Keju krim', '1 potong kecil', 1, 'potong', 15, [], 'Tidak ada baris keju krim di TKPI 2020.'],
  ['minyak-kelapa', 41, T_LJ, 'Minyak kelapa', '1 sendok teh', 1, 'sdt', 5, ['TKPI_KR011']],
  ['minyak-sawit', 41, T_LJ, 'Minyak sawit', '1 sendok teh', 1, 'sdt', 5, ['TKPI_KR012']],
  // page 14 + 89: sayuran 1 porsi = 1 gelas setelah dimasak dan ditiriskan = 100 g
  ['sayur-porsi', 89, T_SAYUR, 'Sayuran (golongan B/C), setelah dimasak dan ditiriskan', '1 gelas', 1, 'gelas', 100,
    ['TKPI_DP001', 'TKPI_DP002', 'TKPI_DP003', 'TKPI_DP004', 'TKPI_DP005', 'TKPI_DP008', 'TKPI_DP009', 'TKPI_DP011', 'TKPI_DP012', 'TKPI_DP013', 'TKPI_DP014', 'TKPI_DP015', 'TKPI_DP019', 'TKPI_DP020', 'TKPI_DP021'],
    'Hal. 14: "1 porsi sayuran kurang lebih 1 gelas sayuran setelah dimasak dan ditiriskan", 100 g per porsi; hal. 89: "Sayuran 1 porsi = 1 gelas = 100 gr". Dipasang hanya ke baris rebus/kukus dari sayuran yang ada di daftar golongan B/C hal. 14-15.'],
  // page 89 keterangan (only the rows not already above)
  ['daging-porsi-89', 89, T_KET, 'Daging (porsi)', '1 potong sedang', 1, 'potong', 35, [], 'Sama dengan hal. 11.'],
  ['ikan-porsi-89', 89, T_KET, 'Ikan segar (porsi)', '1/3 ekor', 1 / 3, 'ekor', 45, [], 'Konflik dengan hal. 11 (1 potong sedang 40 g); acuan saja.'],
  ['susu-rendah-lemak-89', 89, T_KET, 'Susu rendah lemak', '4 sendok makan', 4, 'sdm', 20, [], 'Bentuk (bubuk) tidak disebut; tidak dipasang.'],
  ['minyak-porsi-89', 89, T_KET, 'Minyak (porsi)', '1 sendok teh', 1, 'sdt', 5, [], 'Sama dengan hal. 41.'],
  ['gula-porsi-89', 89, T_KET, 'Gula (porsi)', '1 sendok makan', 1, 'sdm', 20, [], 'Konflik dengan hal. 37 (10 g); tidak dipakai.'],
];

// ---- Our own estimates (⚠). Every row has a method. ---------------------------------
// [foodId, label, qty, unit, grams, spreadPct, method]
const EST = [
  ['TKPI_AP001', '1 centong', 1, 'centong', 100, 30, 'Penilaian: 1 centong nasi rata dianggap sama dengan porsi PMK ¾ gelas = 100 g (hal. 8-9). Belum ditimbang.'],
  ['TKPI_AP001', '1 piring', 1, 'piring', 200, 30, 'Penilaian: 1 piring nasi = 2 centong; sama dengan W_NASI_PUTIH (C-04, juga perkiraan). Belum ditimbang.'],
  ['TKPI_AP005', '1 centong', 1, 'centong', 100, 30, 'Penilaian: sama dengan centong nasi putih (porsi PMK ¾ gelas = 100 g). Belum ditimbang.'],
  ['TKPI_AP017', '1 centong', 1, 'centong', 100, 30, 'Penilaian: sama dengan centong nasi putih (porsi PMK ¾ gelas = 100 g). Belum ditimbang.'],
  ['W_NASI_PUTIH', '1 centong', 1, 'centong', 100, 30, 'Penilaian: porsi PMK ¾ gelas = 100 g (hal. 9). Belum ditimbang.'],
  ['TKPI_CP076', '1 potong sedang', 1, 'potong', 25, 30, 'Ukuran potong sama dengan tempe mentah PMK (2 potong sedang = 50 g, hal. 10); perubahan berat saat digoreng tidak diukur.'],
  ['TKPI_CP083', '1 potong sedang', 1, 'potong', 25, 30, 'Ukuran potong sama dengan tempe mentah PMK (2 potong sedang = 50 g, hal. 10); perubahan berat saat digoreng tidak diukur.'],
  ['TKPI_CP062', '1 potong sedang', 1, 'potong', 50, 40, 'Ukuran potong sama dengan tahu mentah PMK (2 potong sedang = 100 g, hal. 10). Tahu kehilangan air saat digoreng, jadi angka ini kemungkinan terlalu tinggi; W_TAHU_GORENG (C-04) memakai 30 g per potong.'],
  ['TKPI_HP001', '1 butir', 1, 'butir', 60, 20, 'Telur ayam mentah PMK 1 butir = 55 g (hal. 11) ditambah kira-kira 1 sdt minyak (5 g, hal. 41) yang terserap; serapan minyak tidak diukur. Sama dengan W_TELUR_DADAR (C-04).'],
  ['TKPI_NP005', '1 sendok makan', 1, 'sdm', 15, 30, 'Sama dengan cairan kental PMK 1 sdm = 15 g (sirup, madu, hal. 37); kecap tidak ditimbang. PMK hal. 38 hanya memberi natrium per sdt, bukan gram.'],
  ['TKPI_NP005', '1 sendok teh', 1, 'sdt', 5, 30, 'Sama dengan PMK 1 sdt minyak = 5 g (hal. 41); kecap tidak ditimbang.'],
  ['TKPI_BP011', '1 biji sedang', 1, 'biji', 135, 30, 'Ukuran sama dengan ubi jalar kuning mentah PMK (1 biji sedang = 135 g, hal. 9); perubahan berat saat dikukus tidak diukur.'],
  // Cooked dishes (masakan): portion judgement, same grams as the meal packages (C-11); not weighed, not from PMK.
  ['TKPI_GP019', '1 bungkus', 1, 'bungkus', 100, 40, 'Penilaian porsi 1 bungkus pepes rumah/warung; sama dengan gram di paket makan C-11. Belum ditimbang; berat tulang dan daun tidak dipisah.'],
  ['TKPI_CP054', '1 bungkus', 1, 'bungkus', 100, 40, 'Penilaian porsi 1 bungkus pepes oncom; sama dengan gram di paket makan C-11. Belum ditimbang.'],
  ['TKPI_GP024', '1 potong', 1, 'potong', 120, 40, 'Penilaian porsi 1 potong ikan patin bakar; sama dengan gram di paket makan C-11. Belum ditimbang.'],
  ['TKPI_GP005', '1 potong', 1, 'potong', 80, 40, 'Penilaian porsi 1 potong bandeng presto; sama dengan gram di paket makan C-11. Belum ditimbang.'],
  ['TKPI_GP065', '1 mangkok', 1, 'mangkok', 150, 40, 'Penilaian porsi 1 mangkok kecil gulai ikan dengan kuah; sama dengan gram di paket makan C-11. Belum ditimbang.'],
  ['TKPI_JP011', '1 gelas', 1, 'gelas', 200, 25, 'Sama dengan PMK susu sapi 1 gelas = 200 g (hal. 11); yoghurt lebih kental, tidak ditimbang.'],
];

// ---- Plausibility per unit (g for 1 unit). Used by the builder to mark suspect rows
// and by tools/check-household.mjs. Wide on purpose; they catch typos, not small errors.
const PER_UNIT = {
  centong: [50, 200], piring: [100, 500], mangkok: [100, 500], gelas: [40, 260],
  sdm: [3, 25], sdt: [1, 8], potong: [5, 300], iris: [10, 60], butir: [5, 80],
  buah: [1, 1500], biji: [1, 300], ekor: [3, 600], lembar: [5, 60], papan: [5, 60], genggam: [10, 80], bungkus: [20, 600],
};
const LIQUID_GELAS = [150, 260]; // susu, sari, jus: about 200-250 ml per gelas

const fmt = n => Number.isInteger(n) ? String(n) : String(Math.round(n * 10) / 10);
const display = (label, grams, range) => `${label} (±${range ? `${range[0]}–${range[1]}` : fmt(grams)} g)`;

const pmkRows = [];
const byFood = new Map();
const add = (id, m) => { if (!byFood.has(id)) byFood.set(id, []); byFood.get(id).push(m); };
const issues = [];

for (const r of PMK) {
  const [id, page, table, name, urt, qty, unit, g, mapTo, note = null, suspectRule = null] = r;
  if (!UNITS[unit]) throw new Error(`${id}: unknown unit ${unit}`);
  const range = Array.isArray(g) ? g : null;
  const grams = range ? (range[0] + range[1]) / 2 : g;
  const perUnit = grams / qty;
  let suspect = null;
  if (suspectRule === 'per-gelas-cairan') suspect = `${fmt(perUnit)} g per gelas; cairan biasanya ${LIQUID_GELAS[0]}-${LIQUID_GELAS[1]} g per gelas`;
  const [lo, hi] = PER_UNIT[unit];
  if (perUnit < lo || perUnit > hi) suspect = `${fmt(perUnit)} g per ${unit}; batas wajar ${lo}-${hi} g`;
  if (suspect && mapTo.length) throw new Error(`${id}: suspect row must not be mapped`);
  for (const f of mapTo) if (!fileOf(f)) throw new Error(`${id}: food ${f} not in foods-tkpi/estimates`);
  pmkRows.push({ id, page, table, name, urt, qty: Math.round(qty * 1000) / 1000, unit, grams, gramsRange: range, perUnitGrams: Math.round(perUnit * 10) / 10, suspect, mappedTo: mapTo, note });
  for (const f of mapTo) {
    add(f, {
      code: unit, label: urt, qty: Math.round(qty * 1000) / 1000, grams,
      ...(range ? { gramsRange: range, gramsNote: 'Rentang dicetak di PMK; grams = nilai tengah (turunan).' } : {}),
      perUnitGrams: Math.round(perUnit * 10) / 10,
      display: display(urt, grams, range), source: 'pmk-41-2014', ref: { pmkRow: id, page }, flag: null,
    });
  }
}
for (const [f, label, qty, unit, grams, spreadPct, method] of EST) {
  if (!fileOf(f)) throw new Error(`estimate: food ${f} not found`);
  add(f, {
    code: unit, label, qty, grams, perUnitGrams: grams / qty, display: display(label, grams), source: 'estimate',
    method, spreadPct, gramsRangeEst: [Math.round(grams * (1 - spreadPct / 100)), Math.round(grams * (1 + spreadPct / 100))], flag: '⚠',
  });
}

const foods = [...byFood.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([foodId, measures]) => ({
  foodId, file: fileOf(foodId), name_id: nameOf(foodId),
  // PMK first, then estimates; within each, smaller weight first
  measures: measures.sort((a, b) => (a.source === b.source ? a.grams - b.grams : a.source === 'pmk-41-2014' ? -1 : 1)),
}));

const all = foods.flatMap(f => f.measures);
const out = {
  schema: 1,
  version: 1,
  task: 'C-05',
  note: 'Takaran rumah tangga (URT) per foodId. Ditampilkan dulu, gram di sampingnya dengan "±" (R_HH). source pmk-41-2014 = dicetak di PMK 41/2014; estimate = perkiraan kami ⚠ dengan metode. Aplikasi menggabung file ini ke food.household lewat foodId (B-04). Jangan edit tangan; jalankan node tools/build-household.mjs.',
  sources: {
    'pmk-41-2014': {
      title: 'Peraturan Menteri Kesehatan RI No. 41 Tahun 2014 tentang Pedoman Gizi Seimbang (Lampiran)',
      url: 'https://jdih.kemkes.go.id/storage/documents/pdfs/2014permenkes041.pdf',
      sha256: '6b030d76043cb8aacdfacfc406f22c7e1a55270a994f5e356c4672dbeef71b90',
      pages: 96,
      readOn: '2026-11-19',
      tablePages: [8, 9, 10, 11, 12, 13, 14, 15, 16, 37, 41, 89],
      basis: 'Tabel penukar (URT dan berat dalam gram). Daftar pustaka PMK hal. 42: DBMP FKUI 1997 dan Penuntun Diet RSCM/AsDI 2007. PMK tidak menyatakan mentah atau matang kecuali nasi dan sayuran (setelah dimasak), dan buah (berat bersih).',
    },
    estimate: { title: 'Perkiraan kami (C-05), belum ditimbang', flag: '⚠' },
  },
  units: UNITS,
  perUnitLimits: PER_UNIT,
  displayFormat: '<label> (±<grams> g)',
  counts: {
    pmkRows: pmkRows.length,
    pmkRowsMapped: pmkRows.filter(r => r.mappedTo.length).length,
    pmkRowsSuspect: pmkRows.filter(r => r.suspect).length,
    foods: foods.length,
    measures: all.length,
    measuresPmk: all.filter(m => m.source === 'pmk-41-2014').length,
    measuresEstimate: all.filter(m => m.source === 'estimate').length,
  },
  foods,
  pmkRows,
  conflicts: [
    { what: 'Gula 1 sdm', a: 'hal. 37: 10 g', b: 'hal. 89: 20 g', used: '10 g (hal. 37, tabel penukar)' },
    { what: 'Ikan segar 1 porsi', a: 'hal. 11: 1 potong sedang 40 g', b: 'hal. 89: 1/3 ekor 45 g', used: 'tidak dipasang ke ikan tertentu' },
    { what: 'Alpokat ½ buah besar', a: 'hal. 15: 50 g', b: 'hal. 41: 60 g', used: '50 g (hal. 15, tabel buah)' },
  ],
};
if (issues.length) { console.error(issues.join('\n')); process.exit(1); }
fs.writeFileSync(path.join(root, 'content', 'household.json'), JSON.stringify(out, null, 1) + '\n');
console.log(`household.json: ${out.counts.foods} foods, ${out.counts.measures} measures (${out.counts.measuresPmk} PMK, ${out.counts.measuresEstimate} estimate ⚠); PMK rows ${out.counts.pmkRows} (${out.counts.pmkRowsMapped} mapped, ${out.counts.pmkRowsSuspect} suspect)`);
