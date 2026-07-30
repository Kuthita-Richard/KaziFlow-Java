@echo off
REM ============================================================
REM KaziFlow ERP — Windows Installer Build Script
REM ============================================================
REM Produces a self-contained .msi / .exe installer using jpackage.
REM Run this ON A WINDOWS MACHINE with JDK 21 installed.
REM
REM jpackage cannot cross-compile — Windows installers must be
REM built on Windows, macOS installers on macOS, Linux on Linux.
REM ============================================================

setlocal

set APP_NAME=KaziFlow ERP
set APP_VERSION=1.0.0
set MAIN_CLASS=com.kaziflow.App
set VENDOR=Richard Kuthita
set ICON=installer\icons\kaziflow.ico

echo.
echo === Step 1: Clean and build the project ===
call mvn clean package -DskipTests
if errorlevel 1 (
    echo BUILD FAILED — fix compile errors before packaging.
    exit /b 1
)

echo.
echo === Step 2: Create custom runtime image with jlink ===
REM Only the JavaFX + java.sql + java.desktop + java.prefs modules needed
REM are included (smaller installer than bundling a full JRE).
if exist target\runtime rmdir /s /q target\runtime

jlink ^
  --module-path "%JAVA_HOME%\jmods" ^
  --add-modules java.base,java.sql,java.desktop,java.prefs,javafx.controls,javafx.fxml,javafx.graphics,javafx.base ^
  --output target\runtime ^
  --strip-debug ^
  --no-header-files ^
  --no-man-pages ^
  --compress=2

if errorlevel 1 (
    echo JLINK FAILED.
    exit /b 1
)

echo.
echo === Step 3: Package with jpackage ===
if exist target\installer rmdir /s /q target\installer
mkdir target\installer

jpackage ^
  --type msi ^
  --name "%APP_NAME%" ^
  --app-version %APP_VERSION% ^
  --vendor "%VENDOR%" ^
  --runtime-image target\runtime ^
  --input target ^
  --main-jar kaziflow-erp-1.0.0-fat.jar ^
  --main-class %MAIN_CLASS% ^
  --icon %ICON% ^
  --dest target\installer ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser ^
  --win-menu-group "KaziFlow ERP" ^
  --description "Offline-first ERP for Kenyan SMEs" ^
  --copyright "Copyright (c) 2026 Richard Kuthita" ^
  --license-file installer\LICENSE.txt

if errorlevel 1 (
    echo JPACKAGE FAILED.
    exit /b 1
)

echo.
echo ============================================================
echo  SUCCESS — Installer created at: target\installer\
echo ============================================================
echo.
dir target\installer\*.msi

endlocal
