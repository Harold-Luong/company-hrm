#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MODULES_DIR="$ROOT_DIR/back-end/modules"
declare -a PROCESS_GROUPS=()

log() {
    printf '[run-all] %s\n' "$*"
}

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        log "Thiếu lệnh bắt buộc: $1"
        exit 1
    fi
}

stop_all() {
    local status=$?

    trap - EXIT INT TERM
    if ((${#PROCESS_GROUPS[@]} > 0)); then
        log "Đang dừng tất cả ứng dụng..."
        for process_group in "${PROCESS_GROUPS[@]}"; do
            kill -TERM -- "-$process_group" 2>/dev/null || true
        done
        wait 2>/dev/null || true
    fi

    exit "$status"
}

start_app() {
    local name=$1
    local directory=$2
    shift 2

    log "Khởi động $name"
    setsid bash -c 'cd -- "$1"; shift; exec "$@"' _ "$directory" "$@" \
        > >(sed -u "s/^/[$name] /") 2>&1 &
    PROCESS_GROUPS+=("$!")
}

require_command bash
require_command java
require_command node
require_command npm
require_command setsid

for wrapper in \
    "$MODULES_DIR/auth-service-main/mvnw" \
    "$MODULES_DIR/employee-service/mvnw" \
    "$MODULES_DIR/calendar-service/mvnw" \
    "$MODULES_DIR/leave-service/mvnw" \
    "$MODULES_DIR/attendance-service/mvnw" \
    "$MODULES_DIR/gateway/mvnw"; do
    if [[ ! -x "$wrapper" ]]; then
        log "Không tìm thấy Maven Wrapper có quyền chạy: $wrapper"
        exit 1
    fi
done

trap stop_all EXIT INT TERM

# Nạp .env riêng trong tiến trình Auth, tránh truyền cấu hình DB/secrets sang service khác.
# Gateway dùng cổng 8080, vì vậy Auth luôn được đặt tại cổng nội bộ 8081.
start_app "auth" "$MODULES_DIR/auth-service-main" \
    bash -ec 'if [[ -f .env ]]; then set -a; source ./.env; set +a; fi; exec env SERVER_PORT=8081 ./mvnw spring-boot:run'
start_app "employee" "$MODULES_DIR/employee-service" \
    env JWT_ACCESS_PUBLIC_KEY=file:../auth-service-main/keys/access-public.pem ./mvnw spring-boot:run
start_app "calendar" "$MODULES_DIR/calendar-service" \
    env JWT_ACCESS_PUBLIC_KEY=file:../auth-service-main/keys/access-public.pem ./mvnw spring-boot:run
start_app "gateway" "$MODULES_DIR/gateway" \
    env JWT_ACCESS_PUBLIC_KEY=file:../auth-service-main/keys/access-public.pem ./mvnw spring-boot:run
start_app "leave" "$MODULES_DIR/leave-service" \
    env JWT_ACCESS_PUBLIC_KEY=file:../auth-service-main/keys/access-public.pem ./mvnw spring-boot:run
start_app "attendance" "$MODULES_DIR/attendance-service" \
    env JWT_ACCESS_PUBLIC_KEY=file:../auth-service-main/keys/access-public.pem ./mvnw spring-boot:run
start_app "frontend" "$ROOT_DIR/frontend" npm run dev

log "Đã khởi chạy 7 ứng dụng. Chờ log sẵn sàng của từng ứng dụng. Frontend: http://localhost:5173, Gateway: http://localhost:8080"
log "Nhấn Ctrl+C để dừng toàn bộ."

# Kết thúc script ngay khi một ứng dụng thoát; trap sẽ dừng các ứng dụng còn lại.
wait -n "${PROCESS_GROUPS[@]}"
