#!/usr/bin/env bash
# sandbox-verify.sh — get a REAL javac + JavaFX toolchain in a locked-down Linux sandbox
# (Ubuntu 24.04, Maven Central blocked, but Ubuntu apt mirrors reachable) and compile the
# whole project. Idempotent; safe to re-run after a sandbox reset. See PROJECT_GUIDE.md §6.
#
# Usage:  tools/sandbox-verify.sh            # setup (if needed) + full compile
#         tools/sandbox-verify.sh --setup    # only set up the toolchain
#
# What it does / why it works:
#   * JDK 21 + gson/jbcrypt/okhttp come from apt. OpenJFX comes from apt too, but only
#     v11 is packaged (project targets 21) — fine for compile-checking controls/layout;
#     newer-only APIs would show up as errors, which is the safe direction.
#   * apt's okhttp is 3.x; the project uses 4.12. The only difference hit is
#     RequestBody.create(String, MediaType) argument order (3 call sites), so the compile
#     runs on a SCRATCH COPY with a 1-class shim. The real source is never modified.
#   * module-info.java is skipped (classpath-mode compile).
#   * For runtime UI tests: `apt-get install xvfb`, then run with
#       xvfb-run -a java -Djava.library.path=$JFX_JNI -cp "$CP:out" YourTest
#     (call Platform.startup(() -> {}) first; Scene.snapshot() can render PNGs to look at).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${SANDBOX_WORK:-/tmp/kaziflow-verify}"
JFX_DIR="$WORK/jfx"
mkdir -p "$WORK"

setup() {
  if ! command -v javac >/dev/null; then
    apt-get update -qq || true          # third-party repo failures are harmless here
    apt-get install -y openjdk-21-jdk-headless
  fi
  apt-get install -y libjbcrypt-java libgoogle-gson-java libokhttp-java >/dev/null
  if ! ls "$JFX_DIR"/x/usr/share/maven-repo/org/openjfx/javafx-controls/11/*.jar >/dev/null 2>&1; then
    mkdir -p "$JFX_DIR" && (cd "$JFX_DIR" && apt-get download libopenjfx-java libopenjfx-jni && \
      for f in *.deb; do dpkg-deb -x "$f" x; done)
  fi
}

classpath() {
  local cp
  cp="$(find "$JFX_DIR/x" -name 'javafx-*-11-linux.jar' | tr '\n' ':')"
  echo "${cp}/usr/share/java/gson.jar:/usr/share/java/jbcrypt.jar:/usr/share/java/okhttp.jar:/usr/share/java/okio.jar"
}

compile() {
  local scratch="$WORK/scratch"
  rm -rf "$scratch" && mkdir -p "$scratch/out" && cp -r "$ROOT/src" "$scratch/"
  sed -i 's/RequestBody\.create(/Rb.create(/' \
    "$scratch/src/main/java/com/kaziflow/services/AIService.java" \
    "$scratch/src/main/java/com/kaziflow/services/MpesaService.java"
  cat > "$scratch/src/main/java/com/kaziflow/services/Rb.java" <<'SHIM'
package com.kaziflow.services;
import okhttp3.*;
class Rb { static RequestBody create(String s, MediaType m) { return RequestBody.create(m, s); } }
SHIM
  find "$scratch/src/main/java" -name '*.java' ! -name module-info.java > "$scratch/sources.txt"
  javac -proc:none -Xlint:-options -Xmaxerrs 500 -d "$scratch/out" -cp "$(classpath)" @"$scratch/sources.txt"
  echo "OK: full project compiles ($(find "$scratch/out" -name '*.class' | wc -l) classes). Output: $scratch/out"
}

setup
[ "${1:-}" = "--setup" ] || compile
