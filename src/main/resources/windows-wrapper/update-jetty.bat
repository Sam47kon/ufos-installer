@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR_FILE=%SCRIPT_DIR%ufos-installer.jar"

if not exist "%JAR_FILE%" (
    for %%f in ("%SCRIPT_DIR%ufos-installer*.jar") do set "JAR_FILE=%%f"
)

if not exist "%JAR_FILE%" (
    echo [ERROR] Не найден ufos-installer.jar рядом со скриптом.
    exit /b 1
)

java -jar "%JAR_FILE%" --mode update-jetty %*