@echo off
REM StreamCaster build script for Windows.
REM Usage: build.bat [variant]
REM   variant: fossDebug (default), gmsDebug, fossRelease, gmsRelease, all
setlocal enabledelayedexpansion

set "VARIANT=%~1"
if "%VARIANT%"=="" set "VARIANT=fossDebug"

set "PROJECT_DIR=%~dp0"
set "GRADLEW=%PROJECT_DIR%gradlew.bat"
set "ARTIFACTS_DIR=%PROJECT_DIR%artifacts"

if not exist "%ARTIFACTS_DIR%" mkdir "%ARTIFACTS_DIR%"

echo === StreamCaster Build ===
echo Variant: %VARIANT%
echo.

if /i "%VARIANT%"=="fossDebug" (
    call :build_variant foss debug Foss Debug
) else if /i "%VARIANT%"=="gmsDebug" (
    call :build_variant gms debug Gms Debug
) else if /i "%VARIANT%"=="fossRelease" (
    call :build_variant foss release Foss Release
) else if /i "%VARIANT%"=="gmsRelease" (
    call :build_variant gms release Gms Release
) else if /i "%VARIANT%"=="all" (
    call :build_variant foss debug Foss Debug
    if errorlevel 1 goto :fail
    call :build_variant gms debug Gms Debug
    if errorlevel 1 goto :fail
    call :build_variant foss release Foss Release
    if errorlevel 1 goto :fail
    call :build_variant gms release Gms Release
    if errorlevel 1 goto :fail
) else (
    echo Unknown variant: %VARIANT%
    echo Options: fossDebug, gmsDebug, fossRelease, gmsRelease, all
    exit /b 1
)

echo.
echo Running unit tests...
call "%GRADLEW%" --no-daemon testFossDebugUnitTest -q
if errorlevel 1 (
    echo Tests FAILED.
    exit /b 1
)
echo Tests passed.

echo.
echo === Build complete ===
dir "%ARTIFACTS_DIR%\*.apk"
exit /b 0

:build_variant
set "FLAVOR=%~1"
set "TYPE=%~2"
set "FLAVOR_CAP=%~3"
set "TYPE_CAP=%~4"
set "TASK=assemble%FLAVOR_CAP%%TYPE_CAP%"
set "APK_PATH=app\build\outputs\apk\%FLAVOR%\%TYPE%\app-%FLAVOR%-%TYPE%.apk"
set "ARTIFACT_NAME=streamcaster-%FLAVOR%-%TYPE%.apk"

echo Building %FLAVOR% %TYPE%...
call "%GRADLEW%" --no-daemon ":app:%TASK%" -q
if errorlevel 1 (
    echo Build FAILED for %FLAVOR% %TYPE%.
    exit /b 1
)
copy /y "%PROJECT_DIR%%APK_PATH%" "%ARTIFACTS_DIR%\%ARTIFACT_NAME%" >nul
echo   -^> artifacts\%ARTIFACT_NAME%
exit /b 0

:fail
echo Build failed.
exit /b 1
