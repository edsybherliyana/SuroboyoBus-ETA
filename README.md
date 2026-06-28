# SuroboyoBus-ETA

Sistem tracking dan estimasi waktu kedatangan Suroboyo Bus berbasis GPS dengan integrasi Firebase Realtime Database dan algoritma Random Forest

## Overview
Project ini mengembangkan aplikasi Android untuk mengirim data lokasi GPS secara real-time, menyimpan data ke Firebase, lalu memproses data tersebut menggunakan model Random Forest berbasis Python untuk menghasilkan estimasi waktu kedatangan (ETA)

## Problem
Informasi kedatangan bus sering kali tidak akurat karena hanya bergantung pada jadwal tetap. Dibutuhkan sistem yang mampu memantau lokasi bus secara real-time dan memberikan estimasi kedatangan berdasarkan data perjalanan aktual

## Solution
Sistem ini terdiri dari beberapa bagian:
- Aplikasi Android untuk pengumpulan data GPS
- Firebase Realtime Database sebagai media penyimpanan dan sinkronisasi data
- Python service untuk memproses data dan menghasilkan prediksi ETA
- Aplikasi Android untuk menampilkan hasil ETA secara real-time

## Features
- Tracking lokasi bus secara real-time
- Penyimpanan data lokasi ke Firebase
- Prediksi ETA menggunakan Random Forest
- Sinkronisasi data secara real-time
- Tampilan informasi kedatangan pada aplikasi Android

## Tech Stack
- Java
- Android Studio
- Firebase Realtime Database
- Python
- Random Forest
- Google Maps API
- Git
- GitHub

## System Flow
1. Aplikasi Android mengambil data GPS
2. Data dikirim ke Firebase
3. Python membaca data dan melakukan prediksi ETA
4. Hasil ETA dikirim kembali ke Firebase
5. Aplikasi menampilkan hasil ETA secara real-time

## My Contributions
- Mengembangkan aplikasi Android berbasis Java
- Mengintegrasikan Firebase Realtime Database
- Melatih model Random Forest menggunakan Python
- Menghubungkan hasil prediksi ETA ke aplikasi
- Melakukan pengujian sistem

## Screenshots
Gambar aplikasi pada folder `screenshots/`

## Architecture
Diagram sistem pada folder `docs/`

## Contact
- Email: edsy.bherliyana.fatimatus.zahro@gmail.com
- GitHub: edsybherliyana
