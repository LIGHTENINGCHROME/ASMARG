<div align="center">
<img src="https://giffiles.alphacoders.com/206/206974.gif" width="100%" alt="ASMARG Banner"/>
 <div style="display: flex; justify-content: center; align-items: center;">
    <img src="assets/logo.png" width="70" alt="ASMARG Logo"/>
    <h1 style="margin: 0;">ASMARG</h1>
  </div>

<h3><em>Smart Offline Attendance Tracker</em></h3>

<p>
Privacy-first • Offline-first • Geofencing • On-device OCR • Jetpack Compose
</p>

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-API%2026+-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/License-APACHE2.0-blue)

</div>
<div align="center">

[![Download Latest APK](https://img.shields.io/badge/Download_APK-2EA44F?style=for-the-badge&logo=android&logoColor=white)](https://github.com/LIGHTENINGCHROME/ASMARG/releases/download/Version2.1.0/ASMARG.apk)

</div>

## 📖 Overview

ASMARG is a modern Android application that automates student attendance tracking while keeping your data completely private. It combines **geofencing**, **on-device OCR**, and **intelligent scheduling** to mark attendance accurately without relying on cloud services.

All processing—including timetable recognition, attendance management, and analytics—is performed locally on the device.

---

## ✨ Features

- 📍 Automatic attendance using GPS geofencing
- 📄 AI-powered OCR for timetable and holiday extraction
- ⏰ Intelligent class scheduling with cutoff checks
- 🔔 Interactive attendance notifications
- 📊 Subject-wise attendance analytics
- 📅 Integrated attendance calendar
- 📈 Effective & Actual attendance calculation
- 📝 Bulk attendance management
- 💾 JSON backup & restore
- 🔒 100% Offline & Privacy-first

---

## 🛡️ Google Play Protect Notice

ASMARG is distributed through **GitHub Releases** and is **not available on the Google Play Store**. Because it is installed from an external source, Android may display a **Google Play Protect** warning. This is normal for sideloaded applications.

### Installation

1. Download the latest APK from **Releases**.
2. Open the APK.
3. Allow **Install unknown apps** if prompted.
4. Tap **Install**.

### If Play Protect Appears

If you downloaded the APK from the **official ASMARG GitHub repository**:

1. Tap **More details** (or **Details**).
2. Choose **Install anyway** (if available).
3. Complete the installation.

> **Note:** The exact wording and available options may vary depending on your Android version and device manufacturer.

### Security

- ✅ Download only from the official GitHub repository.
- ✅ Verify the release version or checksum (if provided).
- ✅ Keep Google Play Protect enabled.

>ASMARG operates entirely offline and does not upload or collect personal data. A Play Protect warning indicates that the app was installed outside the Play Store—it does **not** necessarily mean the app is unsafe.

---
## 🛠 Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM + StateFlow |
| Database | Room |
| Background Tasks | WorkManager |
| OCR | Google ML Kit (On-Device) |
| Navigation | Jetpack Compose Navigation |

---

## 🏗 Architecture

```
UI (Jetpack Compose)
        │
        ▼
   ViewModel
        │
        ▼
 Repository
   ├── Room Database
   ├── WorkManager
   ├── Location Services
   ├── ML Kit OCR
   └── Local Storage
```

---

## 🔐 Privacy

ASMARG follows an **offline-first** design.

- No cloud storage
- No user accounts
- No external servers
- No location history uploaded
- All data remains on your device

---

## 📱 Permissions

| Permission | Purpose |
|------------|---------|
| Location (Always) | Automatic attendance via geofencing |
| Camera | Scan timetable and holiday documents |
| Notifications | Attendance alerts and quick actions |

---

## 🤝 Contributing

Contributions are welcome!

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push the branch
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **Apache-2.0 license**.

---

## ⭐ Support

If you find this project useful, consider giving it a ⭐ on GitHub.

It helps others discover the project and motivates further development.

---
## NOTE✨
This app is made for my convenience and is not my final year project😉
