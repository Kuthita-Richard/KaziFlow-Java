#!/bin/bash
# ============================================================
# KaziFlow ERP — macOS Installer Build Script
# ============================================================
# Produces a self-contained .dmg installer using jpackage.
# Run this ON A MAC with JDK 21 installed (Apple Silicon or Intel).
#
# jpackage cannot cross-compile — macOS installers must be built
# on macOS. The output .dmg is architecture-specific (build on
# Apple Silicon for an arm64 dmg, Intel Mac for x86_64).
# ============================================================
set -e

APP_NAME="KaziFlow ERP"
APP_VERSION="1.0.0"
MAIN_CLASS="com.kaziflow.App"
VENDOR="Richard Kuthita"
ICONSET_DIR="installer/icons/kaziflow.iconset"
ICNS_FILE="installer/icons/kaziflow.icns"

echo ""
echo "=== Step 1: Clean and build the project ==="
mvn clean package -DskipTests

echo ""
echo "=== Step 2: Build .icns from iconset (macOS-only tool) ==="
if [ -d "$ICONSET_DIR" ]; then
    iconutil -c icns "$ICONSET_DIR" -o "$ICNS_FILE"
    echo "Created $ICNS_FILE"
else
    echo "WARNING: $ICONSET_DIR not found — using default jpackage icon."
    ICNS_FILE=""
fi

echo ""
echo "=== Step 3: Create custom runtime image with jlink ==="
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
echo "=== Step 4: Package with jpackage (.dmg) ==="
rm -rf target/installer
mkdir -p target/installer

ICON_ARG=""
if [ -n "$ICNS_FILE" ] && [ -f "$ICNS_FILE" ]; then
    ICON_ARG="--icon $ICNS_FILE"
fi

jpackage \
  --type dmg \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "$VENDOR" \
  --runtime-image target/runtime \
  --input target \
  --main-jar kaziflow-erp-1.0.0-fat.jar \
  --main-class "$MAIN_CLASS" \
  $ICON_ARG \
  --dest target/installer \
  --mac-package-name "KaziFlow ERP" \
  --description "Offline-first ERP for Kenyan SMEs" \
  --copyright "Copyright (c) 2026 Richard Kuthita" \
  --license-file installer/LICENSE.txt

echo ""
echo "============================================================"
echo " SUCCESS — Installer created at: target/installer/"
echo "============================================================"
ls -la target/installer/*.dmg

echo ""
echo "NOTE — Apple Notarization:"
echo "  This build produces an UNSIGNED .dmg. To distribute outside"
echo "  the App Store without macOS Gatekeeper warnings, you must:"
echo "    1. Obtain an Apple Developer ID certificate (\$99/yr)"
echo "    2. codesign the .app bundle inside target/installer/"
echo "    3. Submit for notarization via 'xcrun notarytool submit'"
echo "    4. Staple the notarization ticket with 'xcrun stapler staple'"
echo "  Without this, users will see an 'unidentified developer' warning"
echo "  and must right-click -> Open to bypass Gatekeeper manually."
