#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DIST="$HOME/.gradle/wrapper/dists/gradle-8.9-bin"
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
JAR=$(find "$DIST" -type f -name gradle-launcher-8.9.jar 2>/dev/null | head -n 1 || true)
if [ -z "$JAR" ]; then
  TMP="$HOME/.gradle/wrapper/dists/clawd-gradle-bootstrap"
  mkdir -p "$TMP"
  ZIP="$TMP/gradle-8.9-bin.zip"
  if [ ! -f "$ZIP" ]; then curl -L --fail --retry 3 -o "$ZIP" https://services.gradle.org/distributions/gradle-8.9-bin.zip; fi
  mkdir -p "$DIST"
  unzip -q -o "$ZIP" -d "$DIST"
  JAR=$(find "$DIST" -type f -name gradle-launcher-8.9.jar | head -n 1)
fi
exec java -classpath "$JAR" org.gradle.launcher.GradleMain "$@"
