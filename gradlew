#!/usr/bin/env sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ ! -f "$SCRIPT_DIR/debug.keystore" ] && [ -f "$SCRIPT_DIR/debug.keystore.base64" ]; then
  base64 -d "$SCRIPT_DIR/debug.keystore.base64" > "$SCRIPT_DIR/debug.keystore"
fi

if [ ! -f "$SCRIPT_DIR/.env" ] && [ -f "$SCRIPT_DIR/.env.example" ]; then
  cp "$SCRIPT_DIR/.env.example" "$SCRIPT_DIR/.env"
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
elif [ -f "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -jar "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
else
  echo "Error: gradle is not installed on PATH. Use gradle/actions/setup-gradle in GitHub Actions." >&2
  exit 1
fi
