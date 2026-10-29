# Build set-up (Windows)

What's needed to build the app on a Windows PC, without Android Studio or an emulator. About 3–5 GB and half an hour. Test on a real TV over adb.

| Piece | Why | Size |
|---|---|---|
| JDK 17 (Eclipse Temurin) | Runs Gradle and the Kotlin compiler | ~300 MB |
| Android SDK command-line tools | `sdkmanager`, to fetch the rest | ~150 MB |
| SDK platform 35 + build tools 35 | Compile against Android 15's API | ~1–2 GB |
| adb (platform-tools) | Install on the TV (you may have it already) | ~15 MB |
| Gradle 8.11.1 | Downloaded automatically by the wrapper on first build | ~150 MB |
| Libraries | Downloaded by Gradle on first build | a few hundred MB |

## 1. JDK 17

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

Close and reopen the terminal, then check:

```powershell
java -version
```

It should say `17.x`. If `JAVA_HOME` isn't set (Gradle complains), set it to the JDK folder (e.g. `C:\Program Files\Eclipse Adoptium\jdk-17...`) in System → Environment variables.

## 2. Android SDK command-line tools

1. Download **"Command line tools only"** for Windows from <https://developer.android.com/studio#command-line-tools-only> (accept the licence on the page).
2. Create `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\` and unzip so that `...\latest\bin\sdkmanager.bat` exists (the zip has a top-level `cmdline-tools` folder: move its contents up into `latest`).
3. Set environment variables (System → Environment variables, user scope):
   - `ANDROID_HOME` = `%LOCALAPPDATA%\Android\Sdk`
   - add `%ANDROID_HOME%\cmdline-tools\latest\bin` and `%ANDROID_HOME%\platform-tools` to `Path`
4. New terminal:
   ```powershell
   sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
   sdkmanager --licenses
   ```

## 3. Gradle wrapper

The repo has `gradle/wrapper/gradle-wrapper.properties` but not yet the wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`). Gradle generates them. Once only, download Gradle, check it against Gradle's published checksum, generate the wrapper, then delete the download:

```powershell
$v = "8.11.1"
$tmp = Join-Path $env:TEMP "gradle-$v"
New-Item -ItemType Directory -Force $tmp | Out-Null
Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$v-bin.zip" -OutFile "$tmp\gradle.zip"
$expected = (Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$v-bin.zip.sha256").Content.Trim()
$actual = (Get-FileHash "$tmp\gradle.zip" -Algorithm SHA256).Hash.ToLower()
if ($actual -ne $expected) { throw "Gradle download checksum mismatch" }
Expand-Archive "$tmp\gradle.zip" -DestinationPath $tmp
& "$tmp\gradle-$v\bin\gradle.bat" wrapper --gradle-version $v
Remove-Item -Recurse -Force $tmp
```

Run it from the repo folder. Commit the three generated files; from then on `.\gradlew.bat` fetches Gradle by itself.

## 4. Build and test

```powershell
.\gradlew.bat test            # unit tests (settings, HTTP server, API auth, pop-ups, MQTT discovery, HA URLs)
.\gradlew.bat assembleDebug   # app\build\outputs\apk\debug\app-debug.apk
```

The first run downloads Gradle and the libraries and takes a few minutes; later builds take seconds.

## 5. Install on a TV

The TV needs developer options and network debugging on (the kit's [TV prep guide](https://github.com/mattgrechtech/ha-tv-kit/blob/main/docs/02-tv-prep.md) covers it). Then:

```powershell
.\tools\provision.ps1 -Tv 192.168.1.60 -StartUrl "http://192.168.1.50:8123/house?tv=1"
```

It installs the APK, grants "display over other apps", excludes the app from battery optimisation, sets the start URL, opens the dashboard and prints the API token.

Logs while testing:

```powershell
adb -s 192.168.1.60:5555 logcat -s KioskActivity HaBridge KioskService MqttBridge PopupManager KioskController
```

## Release builds (later)

Release builds need a signing key. Create it once, keep it **outside the repo** in a password manager plus a backup (losing it means users must uninstall to update):

```powershell
keytool -genkeypair -v -keystore $HOME\keys\tv-kiosk-release.jks -alias release -keyalg RSA -keysize 4096 -validity 10000
```

Signing configuration comes when the first release is cut.
