#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MODULES_DIR="$ROOT_DIR/back-end/modules"

log() {
    printf '[run-all] %s\n' "$*"
}

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        log "Thiếu lệnh bắt buộc: $1"
        exit 1
    fi
}

require_command bash
require_command java
require_command node
require_command npm
require_command mvn

WRAPPER_NAME=mvnw
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) WRAPPER_NAME=mvnw.cmd ;;
esac

if [[ ! -f "$ROOT_DIR/frontend/node_modules/vite/bin/vite.js" ]] ||
   [[ ! -f "$ROOT_DIR/frontend/node_modules/.bin/vite" ]]; then
    log "Thiếu dependency frontend (Vite). Chạy: cd frontend && npm ci --include=dev"
    exit 1
fi

for wrapper in \
    "$MODULES_DIR/auth-service-main/$WRAPPER_NAME" \
    "$MODULES_DIR/employee-service/$WRAPPER_NAME" \
    "$MODULES_DIR/calendar-service/$WRAPPER_NAME" \
    "$MODULES_DIR/leave-service/$WRAPPER_NAME" \
    "$MODULES_DIR/attendance-service/$WRAPPER_NAME" \
    "$MODULES_DIR/gateway/$WRAPPER_NAME"; do
    if [[ ! -f "$wrapper" ]] || { [[ "$WRAPPER_NAME" == mvnw ]] && [[ ! -x "$wrapper" ]]; }; then
        log "Không tìm thấy Maven Wrapper có quyền chạy: $wrapper"
        exit 1
    fi
done

# Pass the current Git Bash executable, not Windows' WSL bash.exe from PATH.
BASH_EXECUTABLE="$BASH"
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) BASH_EXECUTABLE="$(cygpath -w "$BASH")" ;;
esac
exec node "$ROOT_DIR/scripts/run-all.mjs" "$BASH_EXECUTABLE"
