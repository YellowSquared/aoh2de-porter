# aoh2de-porter

Android port of Age of History II: Definitive Edition (libGDX).

---

## How to Build Debug APK

### 1. Prerequisites

#### A. Install Azul Zulu Java 25
Make sure **Zulu OpenJDK 25** is installed:

- **mise (recommended):**
  ```bash
  mise use -g java@zulu-25
  ```
- **Windows (winget):**
  ```powershell
  winget install Azul.Zulu.25.JDK
  ```
- **macOS (Homebrew):**
  ```bash
  brew install --cask zulu@25
  ```
- **Linux / macOS (SDKMAN!):**
  ```bash
  sdk install java 25-zulu
  ```
- Or download directly from [Azul Zulu Downloads](https://www.azul.com/downloads/?version=java-25&package=jdk).

*(Ensure `JAVA_HOME` points to your Java 25 installation or that Java 25 is the active JDK in your terminal).*

#### B. Android SDK
The easiest way to set up the Android SDK:
1. Download and install [Android Studio](https://developer.android.com/studio).
2. Launch Android Studio and walk through the initial setup wizard (accept the default components and licenses to download the SDK).
3. The SDK will be installed to the default location:
   - **Windows:** `C:\Users\<username>\AppData\Local\Android\Sdk`
   - **macOS:** `/Users/<username>/Library/Android/sdk`
   - **Linux:** `/home/<username>/Android/Sdk`
4. Point this project to your SDK by creating a `local.properties` file in the repository root:
   ```properties
   sdk.dir=C:\\Users\\<username>\\AppData\\Local\\Android\\Sdk   # on Windows
   # sdk.dir=/Users/<username>/Library/Android/sdk             # on macOS
   # sdk.dir=/home/<username>/Android/Sdk                      # on Linux
   ```
   *(Or configure the `ANDROID_HOME` / `ANDROID_SDK_ROOT` environment variable).*
   *(Gradle will automatically download API 36 / `compileSdk 36` on the first build if not already present).*

#### C. Build Inputs
- Put your desktop `game.jar` into: `libs/game.jar`
- Put the game data assets folder into: `assets/`

---

### 2. Build the Debug APK

Run the Gradle build command in the root folder:

- **Windows:**
  ```powershell
  .\gradlew.bat :android:assembleDebug
  ```

- **Linux / macOS:**
  ```bash
  ./gradlew :android:assembleDebug
  ```

Your output APK will be generated at:
```
android/build/outputs/apk/debug/android-debug.apk
```

---

### 3. (Optional) Install & Run

To build, install to an attached device or running emulator, and launch automatically:

- **Windows:**
  ```powershell
  .\gradlew.bat :android:run
  ```
- **Linux / macOS:**
  ```bash
  ./gradlew :android:run
  ```

---

## Project Structure

- `android`: Android mobile launcher, asset packager, and lifecycle handler.
- `core`: Shared game application logic and runtime helpers.
- `patch`: Bytecode patchers (ASM) run against `game.jar` at build time.
- `ios`: iOS platform launcher (RoboVM).
