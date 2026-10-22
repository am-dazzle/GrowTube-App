# GrowTube 🚀
### AI-Powered YouTube Channel Growth Analyzer & Strategy Studio for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Gemini AI](https://img.shields.io/badge/AI-Google_Gemini_API-8E75C4.svg?logo=google&logoColor=white)](https://ai.google.dev/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Download APK](https://img.shields.io/badge/Download-Latest_APK-blue?logo=android&logoColor=white)](https://github.com/YOUR_USERNAME/growtube/releases/latest)

**GrowTube** is a modern, native Android application engineered for YouTube content creators and growth strategists. It combines live YouTube metrics with Google's Gemini AI to analyze channel performance, track competitors, generate high-converting title & thumbnail ideas, and output complete 30-day viral content calendars.

---

## 📥 Download & Install APK

Directly install GrowTube on your Android phone without building from source:

1. Go to the [**Releases Page**](https://github.com/YOUR_USERNAME/growtube/releases).
2. Download the latest `GrowTube-vX.X.apk` (or `app-debug.apk`) under **Assets**.
3. On your Android device, tap the downloaded file and select **Install** (enable *"Install unknown apps"* if prompted in Android Settings).


## 🌟 Key Features

- **🧬 Channel DNA Analyzer**:
  - Live metric breakdown: Total views, subscribers, video counts, and calculated average performance.
  - Channel Health Score & upload consistency radar.
  - Audience retention strategies tailored to creator niche.

- **⚔️ Competitor Intelligence Tracker**:
  - Side-by-side comparison with benchmark channels in your niche.
  - Video velocity analysis and content gap detection.

- **🤖 30-Day AI Content Engine (Gemini-Powered)**:
  - Generate full 30-day production schedules with 1 click.
  - Click-worthy title suggestions, hook frameworks, and SEO-optimized descriptions.
  - Thumbnail visual design prompts and color scheme guidance.

- **🚀 Launch & SEO Optimizer**:
  - Peak upload timing recommendations based on target audience demographics.
  - High-traffic tag generator and video description templates.

- **🔒 Privacy & Zero Secret Exposure**:
  - Real YouTube Data API v3 integration with on-device encrypted key storage.
  - Server-side / injected Gemini API configuration using Secrets Gradle Plugin.
  - Offline/fallback mode available when API keys are not supplied.

---

## 🏗️ Architecture & Tech Stack

```
               ┌────────────────────────────────────────────────────────┐
               │                  Jetpack Compose UI                    │
               │  (Material 3, Adaptive Layouts, Dynamic Color Theming) │
               └───────────────────────────┬────────────────────────────┘
                                           │
                                           ▼
               ┌────────────────────────────────────────────────────────┐
               │                   GrowTubeViewModel                    │
               │         (StateFlow, Coroutines, MVI/MVVM)              │
               └─────────────────────┬──────────────────┬───────────────┘
                                     │                  │
                ┌────────────────────┴───┐          ┌───┴────────────────────┐
                ▼                        ▼          ▼                        ▼
     ┌──────────────────────┐  ┌──────────────────┐  ┌────────────────┐  ┌────────────────┐
     │ YouTube Data API v3  │  │ Gemini Generative│  │ Encrypted      │  │ Local Fallback │
     │ (Channels, Playlists,│  │ AI Client        │  │ SharedPreferences│  │ Mock Analytics │
     │  Videos & Analytics) │  │ (Strategy Engine)│  │ (User Keys)    │  │ Engine         │
     └──────────────────────┘  └──────────────────┘  └────────────────┘  └────────────────┘
```

- **UI Toolkit**: 100% Jetpack Compose with Material Design 3 (`ColorScheme`, `Typography`, `Shapes`).
- **State Architecture**: MVVM with Kotlin Coroutines and `MutableStateFlow` / `collectAsStateWithLifecycle`.
- **Networking**: Ktor / OkHttp with JSON serialization for Google APIs.
- **Security**: Android Secrets Gradle Plugin via `.env` for zero-leak repository commits.
- **Build System**: Gradle Kotlin DSL (`.gradle.kts`) with Version Catalog (`libs.versions.toml`).

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1+) or newer
- **JDK**: Version 17 or 21
- **Android SDK**: `minSdk 26`, `compileSdk 36`
- **Gemini API Key**: Free key from [Google AI Studio](https://aistudio.google.com/)

### Installation & Setup

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/YOUR_USERNAME/growtube.git
   cd growtube
   ```

2. **Configure Environment Secrets**:
   Copy `.env.example` to create `.env`:
   ```bash
   cp .env.example .env
   ```
   Add your Gemini API key in `.env`:
   ```properties
   GEMINI_API_KEY=your_actual_gemini_api_key_here
   ```
   *(Note: `.env` is included in `.gitignore` and will never be committed to Git).*

3. **Build & Run**:
   - Open the project directory in **Android Studio**.
   - Sync Gradle project files (`File -> Sync Project with Gradle Files`).
   - Connect an Android device or launch an emulator.
   - Click **Run** (`Shift + F10`) or use Gradle CLI:
     ```bash
     gradle assembleDebug
     ```

---

## 🔑 YouTube API Setup (Optional)
GrowTube functions with rich offline heuristics out of the box. For live channel synchronization:
1. Obtain a YouTube Data API v3 key from [Google Cloud Console](https://console.cloud.google.com/).
2. Open GrowTube on your device and tap **Settings (⚙️)**.
3. Paste your YouTube API key. It is saved securely in your device's private app storage.

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!
Please review [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a Pull Request.

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.
