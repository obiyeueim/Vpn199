@echo off
setlocal
set GRADLE_VERSION=8.13
set CACHE_DIR=%USERPROFILE%\.gradle\pingpilot-bootstrap
set ZIP_FILE=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip
set GRADLE_HOME=%CACHE_DIR%\gradle-%GRADLE_VERSION%
if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%ZIP_FILE%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'"
    if errorlevel 1 exit /b 1
  )
  if exist "%GRADLE_HOME%" rmdir /s /q "%GRADLE_HOME%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%CACHE_DIR%' -Force"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
endlocal
