#!/usr/bin/env bash
#
# update-stand.sh
# Обёртка для обновления ядра и приклада стенда УФОС.
#
# Использование:
#   ./update-stand.sh --stand-dir /opt/ufos/STAND --artifacts-dir /opt/ufos/install/artifacts
#
# Скрипт ищет ufos-installer.jar в следующих местах:
#   1. переменная окружения UFOS_INSTALLER_JAR;
#   2. каталог, где лежит сам скрипт;
#   3. родительский каталог;
#   4. каталог ../../../../target, удобно при запуске из исходников;
#   5. текущий рабочий каталог.
#

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] Java не найдена в PATH. Требуется Java 17+."
    exit 1
fi

JAR=""

find_in_dir() {
    local dir
    local found

    dir="$1"

    if [ ! -d "$dir" ]; then
        return 1
    fi

    if [ -f "$dir/ufos-installer.jar" ]; then
        JAR="$dir/ufos-installer.jar"
        return 0
    fi

    found=$(find "$dir" -maxdepth 1 -type f -name 'ufos-installer*.jar' 2>/dev/null | sort | head -n 1 || true)

    if [ -n "$found" ]; then
        JAR="$found"
        return 0
    fi

    return 1
}

if [ -n "$UFOS_INSTALLER_JAR" ]; then
    if [ ! -f "$UFOS_INSTALLER_JAR" ]; then
        echo "[ERROR] UFOS_INSTALLER_JAR указывает на несуществующий файл: $UFOS_INSTALLER_JAR"
        exit 1
    fi

    JAR="$UFOS_INSTALLER_JAR"
fi

if [ -z "$JAR" ]; then
    find_in_dir "$SCRIPT_DIR" ||
    find_in_dir "$SCRIPT_DIR/.." ||
    find_in_dir "$SCRIPT_DIR/../../../../target" ||
    find_in_dir "$(pwd)" || {
        echo "[ERROR] Не найден ufos-installer.jar."
        echo "Положите ufos-installer.jar рядом со скриптом или задайте переменную окружения UFOS_INSTALLER_JAR."
        exit 1
    }
fi

exec java -jar "$JAR" --mode update-core "$@"