# Gemini Native Android Wrapper

A native Kotlin Android application that wraps `Gemini.google.com` with official branding and modern Android practices.

## Features
- **Android 16 Support**: Targeting API 36 (Baklava).
- **Architecture**: Optimized for `arm64-v8a`.
- **WebView Integration**: High-performance WebView with JS and DOM storage enabled.
- **CI/CD**: Ready-to-use GitHub Actions workflow for automated APK generation.
- **Official Branding**: Uses Gemini-inspired colors and icons.

## Project Structure
- `app/`: Main Android module.
- `.github/workflows/`: CI/CD configuration.
- `gradle/`: Gradle wrapper and configuration.

## How to Build
1. **Local Build**:
   - Open the project in Android Studio.
   - Run `./gradlew assembleDebug` to generate a debug APK.
2. **GitHub Actions**:
   - Push this repository to GitHub.
   - Go to the **Actions** tab to see the build progress.
   - Once finished, download the APK from the **Artifacts** section of the build summary.

## Configuration
- **Package Name**: `com.gemini.ai`
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 16)

## License
Apache-2.0
