#!/bin/bash
# ============================================================
# KaziFlow ERP — Linux Installer Build Script
# ============================================================
# Produces a self-contained .deb (Debian/Ubuntu) installer using
# jpackage. Run this ON A LINUX MACHINE with JDK 21 installed.
#
# For .rpm (Fedora/RHEL) instead, change --type deb to --type rpm
# (requires rpm-build package installed).
# ============================================================
set -e

APP_NAME="kaziflow-erp"
APP_DISPLAY_NAME="KaziFlow ERP"
APP_VERSION="1.0.0"
MAIN_CLASS="com.kaziflow.App"
VENDOR="Richard Kuthita"
ICON="installer/icons/kaziflow_256.png"

echo ""
echo "=== Step 1: Clean and build the project ==="
mvn clean package -DskipTests

echo ""
echo "=== Step 2: Create custom runtime image with jlink ==="
rm -rf target/runtime
jlink \
  --module-path "$JAVA_HOME/jmods" \
  --add-modules java.base,java.sql,java.desktop,java.prefs,javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
  --output target/runtime \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --compress=2

echo ""
echo "=== Step 3: Package with jpackage (.deb) ==="
rm -rf target/installer
mkdir -p target/installer

jpackage \
  --type deb \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "$VENDOR" \
  --runtime-image target/runtime \
  --input target \
  --main-jar kaziflow-erp-1.0.0-fat.jar \
  --main-class "$MAIN_CLASS" \
  --icon "$ICON" \
  --dest target/installer \
  --linux-shortcut \
  --linux-menu-group "Office" \
  --linux-app-category "office" \
  --description "Offline-first ERP for Kenyan SMEs" \
  --copyright "Copyright (c) 2026 Richard Kuthita" \
  --license-file installer/LICENSE.txt

echo ""
echo "============================================================"
echo " SUCCESS — Installer created at: target/installer/"
echo "============================================================"
ls -la target/installer/*.deb

echo ""
echo "Install with:  sudo dpkg -i target/installer/${APP_NAME}_${APP_VERSION}*.deb"
echo "Or run directly without installing from target/runtime/bin/"
