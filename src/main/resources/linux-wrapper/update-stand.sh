#!/usr/bin/env bash
#
# update-stand.sh
# Обёртка для обновления ядра и приклада без пересоздания Jetty
#

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] Java не найдена в PATH. Требуется Java 17+."
    exit 1
fi

find_jar() {
    local jar="$SCRIPT_DIR/ufos-installer.jar"

    if [[ -f "$jar" ]]; then
        echo "$jar"
        return
    fi

    jar="$(find "$SCRIPT_DIR" -maxdepth 1 -type f -name 'ufos-installer*.jar' | head -n 1 || true)"

    if [[ -n "$jar" ]]; then
        echo "$jar"
        return
    fi

    return 1
}

JAR_FILE="$(find_jar)" || {
    echo "[ERROR] Не найден ufos-installer.jar рядом со скриптом."
    exit 1
}

exec java -jar "$JAR_FILE" --mode update-core "$@"