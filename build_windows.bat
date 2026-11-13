@echo off
setlocal

if "%ANDROID_HOME%"=="" if not "%ANDROID_SDK_ROOT%"=="" set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if "%ANDROID_HOME%"=="" (
  echo ERROR: Set ANDROID_HOME to your Android SDK folder first.
  echo Example: set ANDROID_HOME=C:\Android\Sdk
  exit /b 1
)

where java >nul 2>nul || (echo ERROR: JDK 17 is not on PATH.& exit /b 1)
where gradle >nul 2>nul || (
  if exist gradlew.bat (
    echo Using existing Gradle Wrapper.
  ) else (
    echo ERROR: Gradle is not installed and this project has no wrapper yet.
    echo Install Gradle 8.9 once, then run: gradle wrapper --gradle-version 8.9
    exit /b 1
  )
)

if not exist gradlew.bat (
  echo Creating Gradle Wrapper 8.9...
  gradle wrapper --gradle-version 8.9 || exit /b 1
)

call gradlew.bat clean assembleDebug
if errorlevel 1 exit /b 1

echo.
echo APK created at:
echo app\build\outputs\apk\debug\app-debug.apk
