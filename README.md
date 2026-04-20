# ☁️ CloudVault — Cloudinary Personal File Browser

A **Material 3 Expressive** Android app for browsing, filtering, and playing back your personal Cloudinary assets (primarily MP3/audio files) with a stunning dark UI.

---

## ✨ Features

| Feature | Details |
|---------|---------|
| 🎵 Instant playback | Tap play on any audio/video card — no extra steps |
| 🎨 Material 3 Expressive | Dark navy theme, animated waveforms, gradient cards |
| 🔍 Smart filters | Name search, date range, before/after file, file type, sort, size |
| 📋 Copy link | One-tap copy of any asset's Cloudinary URL |
| ℹ️ Asset info | Full metadata dialog per file |
| 🔒 Local credentials | API keys stored only on device via DataStore |
| ♾️ Pagination | Automatically fetches all pages from Cloudinary |
| 🎬 Mini player bar | Persistent bottom bar with progress while audio plays |

---

## 📱 Screenshots layout (mock reference)

```
┌──────────┬──────────┐
│ 🎵 Naat │ 📄 Q3.xl │   ← Row 1
│  [▶️]    │  [▶️]   │
├──────────┼──────────┤
│ 🎵 Demo │ 📁 Notes │   ← Row 2
│  [▶️]    │  [▶️]   │
├──────────┼──────────┤
│ 📊 Deck │ 🎬 Sync  │   ← Row 3
│  [▶️]    │  [▶️]   │
└──────────┴──────────┘
━━━━━ 🎵 Playing: naat_001  [⏸] [✕] ━━━
```

---

## 🚀 Getting the APK

### Option A — GitHub Actions (Recommended)

1. **Fork or push this repo to GitHub**
2. Go to `Actions` → `Build APK` → `Run workflow`
3. The debug APK is uploaded as an artifact after ~3–5 minutes
4. Download and install on your device (enable "Install from unknown sources")

### Option B — Android Studio (Local)

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug or newer)
2. Open the `cloudinary-app` folder in Android Studio
3. Wait for Gradle sync to complete (~2–4 min first time)
4. Build → **Build APK(s)** → find APK at `app/build/outputs/apk/debug/app-debug.apk`

### Option C — Command line (needs JDK 17 + Gradle 8.9)

```bash
# Install Gradle 8.9 if not already installed
# Then from the cloudinary-app/ directory:

gradle wrapper --gradle-version=8.9
chmod +x gradlew
./gradlew assembleDebug

# APK output:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ First-time Setup in the App

1. Open the app → **Setup screen** appears
2. Enter your Cloudinary details:
   - **Cloud Name**: found in Cloudinary Console → Dashboard (e.g. `my-cloud-abc`)
   - **API Key**: Settings → API Keys
   - **API Secret**: Settings → API Keys (click reveal)
3. Tap **Connect** — credentials saved locally
4. Assets load automatically

> 🔒 Your credentials never leave the device. They're stored using Android DataStore with no network transmission.

---

## 🔍 Filter Guide

| Filter | How it works |
|--------|-------------|
| **Search by name** | Contains / Exact / Starts with / Ends with |
| **Date range** | Pick start + end date AND time (hour:minute) |
| **Before/After file** | Enter a filename, show all files uploaded before or after it |
| **File type** | All / Audio / Video / Image / PDF / Other |
| **Sort** | Newest, Oldest, Name A→Z, Name Z→A, Largest, Smallest |
| **File size** | Slider from 0–500 MB |

Active filter count shown on FAB badge. "Reset All" in filter sheet clears everything.

---

## 📦 Project Structure

```
cloudinary-app/
├── .github/workflows/build-apk.yml    ← CI/CD
├── app/src/main/java/com/cloudinaryfiles/app/
│   ├── MainActivity.kt
│   ├── data/
│   │   ├── api/CloudinaryApi.kt        ← Retrofit endpoints
│   │   ├── model/                      ← Data classes
│   │   ├── preferences/UserPreferences.kt ← DataStore
│   │   └── repository/CloudinaryRepository.kt
│   └── ui/
│       ├── components/
│       │   ├── FileCard.kt             ← Individual file card
│       │   ├── FilterBottomSheet.kt    ← All filter UI
│       │   └── AudioPlayerBar.kt       ← Bottom player
│       ├── screens/
│       │   ├── SetupScreen.kt          ← Credential entry
│       │   └── FilesScreen.kt          ← Main grid screen
│       ├── theme/                      ← M3 colors/typography
│       └── viewmodel/FilesViewModel.kt ← All state & logic
```

---

## 🔑 Release APK with Signing (optional)

To get a signed release APK via GitHub Actions:

1. Generate a keystore:
   ```bash
   keytool -genkey -v -keystore cloudvault.jks \
     -alias cloudvault -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Base64-encode it:
   ```bash
   base64 cloudvault.jks | pbcopy   # macOS
   base64 cloudvault.jks | xclip    # Linux
   ```
3. Add GitHub Secrets:
   - `KEYSTORE_BASE64` — the base64 string
   - `KEY_ALIAS` — your alias (e.g. `cloudvault`)
   - `KEYSTORE_PASSWORD` — keystore password
   - `KEY_PASSWORD` — key password
4. Trigger workflow with `build_type = release`

---

## 🛠 Tech Stack

- **Kotlin** 2.0 + **Jetpack Compose** (Material 3)
- **ExoPlayer (Media3)** for audio streaming
- **Retrofit 2** + **OkHttp** for Cloudinary API
- **DataStore Preferences** for secure local storage
- **Coil** for image loading
- **Navigation Compose** for screen routing

---

## 📋 Requirements

- Android 8.0+ (API 26+)
- Internet connection
- Cloudinary account with API access enabled

---

*Built with ❤️ — CloudVault is a personal tool and is not affiliated with Cloudinary.*
