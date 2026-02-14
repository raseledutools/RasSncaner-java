# Contributing to MakeACopy

Thank you for your interest in contributing to MakeACopy! This guide explains how to set up a development environment on **Linux** and **macOS**.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| **JDK** | 17 (Temurin recommended) | Required for Gradle and Android builds |
| **Android SDK** | API 36 | `compileSdk = 36`, `minSdk = 29` |
| **Android NDK** | 28.0.13004108 | Exact version required |
| **CMake** | 3.31.6 | Must match F-Droid reproducible build |
| **Python** | 3.11.x | Used by CMake scripts during native builds |
| **Git** | any recent | With submodule support |

## 1. Clone the Repository

```bash
git clone --recurse-submodules https://github.com/egdels/makeacopy.git
cd makeacopy
```

If you already cloned without `--recurse-submodules`:

```bash
git submodule update --init --recursive
```

The project uses two Git submodules:
- `external/opencv` — OpenCV 4.13.0
- `external/onnxruntime` — ONNX Runtime v1.24.1

## 2. Install JDK 17

### macOS

```bash
brew install openjdk@17
```

Or install [Eclipse Temurin](https://adoptium.net/) manually.

### Linux (Debian/Ubuntu)

```bash
sudo apt-get install -y openjdk-17-jdk
```

### Linux (Fedora)

```bash
sudo dnf install -y java-17-openjdk-devel
```

Verify:

```bash
java -version   # should show 17.x
```

## 3. Install Android SDK and NDK

### Option A: Android Studio (recommended)

1. Install [Android Studio](https://developer.android.com/studio).
2. Open **SDK Manager** → **SDK Platforms** → install **Android API 36**.
3. Under **SDK Tools**, install:
   - **NDK (Side by side)** version **28.0.13004108**
   - **CMake** (see section below for exact version)
4. Set the environment variable:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"          # Linux
export ANDROID_HOME="$HOME/Library/Android/sdk"   # macOS
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.0.13004108"
```

### Option B: Command-line only

```bash
# Install NDK via sdkmanager
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "ndk;28.0.13004108" \
  "platforms;android-36" \
  "build-tools;36.0.0"
```

## 4. Install CMake 3.31.6

The project requires CMake **3.31.6** to match F-Droid reproducible builds.

### macOS

```bash
brew install cmake
# If brew installs a different version, download 3.31.6 manually:
# https://github.com/Kitware/CMake/releases/tag/v3.31.6
```

### Linux

```bash
# Download and install CMake 3.31.6
wget https://github.com/Kitware/CMake/releases/download/v3.31.6/cmake-3.31.6-linux-x86_64.tar.gz
tar xzf cmake-3.31.6-linux-x86_64.tar.gz
export PATH="$PWD/cmake-3.31.6-linux-x86_64/bin:$PATH"
```

Verify:

```bash
cmake --version   # should show 3.31.6
```

## 5. Install Python 3.11

Python is needed by CMake scripts during native library builds.

### macOS

```bash
brew install python@3.11
```

### Linux (Debian/Ubuntu)

```bash
sudo apt-get install -y python3.11
```

Verify:

```bash
python3 --version   # should show 3.11.x
```

## 6. Build Native Libraries

Before building the Android app, you must compile the native libraries (OpenCV and ONNX Runtime) from source.

### Set environment variables

```bash
export ANDROID_HOME="$HOME/Android/Sdk"                    # adjust for your system
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.0.13004108"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ORT_CMAKE="$(which cmake)"
export OPENCV_CMAKE="$(which cmake)"
export PY3_BIN="$(which python3)"
export BUILD_GENERATOR="Unix Makefiles"
```

### Build OpenCV

```bash
chmod +x scripts/build_opencv_android.sh
VERBOSE=1 ./scripts/build_opencv_android.sh
```

### Prepare OpenCV for the app

```bash
chmod +x scripts/prepare_opencv.sh
./scripts/prepare_opencv.sh
```

This copies the compiled `.so` files into `app/src/main/jniLibs/`.

### Build ONNX Runtime

```bash
chmod +x scripts/build_onnxruntime_android.sh
./scripts/build_onnxruntime_android.sh
```

> **Tip:** To build only for a specific ABI (faster for development):
> ```bash
> ABIS="arm64-v8a" ./scripts/build_opencv_android.sh
> ABIS="arm64-v8a" ./scripts/prepare_opencv.sh
> ABIS="arm64-v8a" ./scripts/build_onnxruntime_android.sh
> ```

## 7. Build the App

```bash
chmod +x gradlew

# Debug APK (with ABI splits)
./gradlew :app:assembleDebug

# Debug APK (single universal APK, faster)
./gradlew :app:assembleDebug -PenableAbiSplits=false

# Release APK
./gradlew :app:assembleRelease
```

## 8. Run Tests

### JVM Unit Tests

```bash
# All unit tests
./gradlew :app:testDebugUnitTest

# Single test class
./gradlew :app:testDebugUnitTest --tests "de.schliweb.makeacopy.ExampleSanityTest"
```

### Instrumented Tests (requires emulator or device)

```bash
# All instrumented tests
./gradlew :app:connectedDebugAndroidTest

# Single test class
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments=class=de.schliweb.makeacopy.SomeInstrumentedTest
```

> The emulator or device must run **API 29+** (matching `minSdk`).

## IDE Setup

### Android Studio / IntelliJ IDEA

1. Open the project root directory.
2. Let Gradle sync complete.
3. Ensure **annotation processing** is enabled (required for Lombok and Room):
   - **Settings → Build → Compiler → Annotation Processors → Enable annotation processing**
4. The project uses **Java 17** — make sure your IDE JDK is set accordingly.

### Lombok

The project uses Lombok (`compileOnly` + `annotationProcessor`). Install the Lombok plugin in your IDE for proper code completion and navigation.

## Project Structure

```
makeacopy/
├── app/                          # Android application module
│   ├── src/main/java/            # Main source code
│   ├── src/test/java/            # JVM unit tests
│   ├── src/androidTest/java/     # Instrumented tests
│   └── src/main/jniLibs/         # Native libraries (generated)
├── external/
│   ├── opencv/                   # OpenCV submodule
│   ├── onnxruntime/              # ONNX Runtime submodule
│   └── opencv_pinned_jni/        # Pinned JNI headers for reproducibility
├── scripts/                      # Build scripts for native libraries
├── fastlane/                     # Fastlane metadata and changelogs
├── docs/                         # Documentation
├── langpack-latin-best/          # OCR language pack module
├── training/                     # ML model training resources
└── server/                       # Server module
```

## Feature Flags

The app uses `BuildConfig` booleans to gate features:

| Flag | Default | Description |
|------|---------|-------------|
| `FEATURE_SCAN_LIBRARY` | `true` | Scan library functionality |
| `FEATURE_REVIEW_OCR` | `true` | OCR review and correction |
| `FEATURE_FRAMING_LOGGING` | `false` | Debug logging for framing engine |
| `FEATURE_A11Y_GUIDANCE` | `true` | Accessibility guidance mode |
| `FEATURE_LAYOUT_ANALYSIS` | `false` | Layout analysis feature |

When adding new features, prefer **default-off** for risky or experimental changes.

## Tips

- **ABI splits**: Enabled by default. Use `-PenableAbiSplits=false` for a single universal APK during development.
- **Hamcrest**: The project pins a single Hamcrest version for `androidTest`. When adding test dependencies, exclude `hamcrest-core` and `hamcrest-library` to avoid duplicate classes.
- **Test PDFs**: Place test assets under `app/src/debug/assets` or test directories, not in `main` — test PDFs are excluded from release builds.
- **Native builds take time**: The first OpenCV and ONNX Runtime builds can take 30–60 minutes. Subsequent builds are faster if the build directories are cached.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
