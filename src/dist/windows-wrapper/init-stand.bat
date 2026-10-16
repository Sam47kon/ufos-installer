@echo off
setlocal EnableExtensions
chcp 65001 >nul

rem
rem init-stand.bat
rem Обёртка для первичной установки стенда УФОС.
rem
rem Использование:
rem   init-stand.bat --stand-dir "C:\ufos\STAND" --artifacts-dir "C:\ufos\install\artifacts"
rem
rem Скрипт ищет ufos-installer.jar в следующих местах:
rem   1. переменная окружения UFOS_INSTALLER_JAR;
rem   2. каталог, где лежит сам скрипт;
rem   3. родительский каталог;
rem   4. каталог ..\..\..\..\target, удобно при запуске из исходников;
rem   5. текущий рабочий каталог.
rem

set "JAR="

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java не найдена в PATH. Требуется Java 17+.
    exit /b 1
)

if defined UFOS_INSTALLER_JAR (
    if exist "%UFOS_INSTALLER_JAR%" (
        set "JAR=%UFOS_INSTALLER_JAR%"
        goto run
    )

    echo [ERROR] UFOS_INSTALLER_JAR указывает на несуществующий файл: %UFOS_INSTALLER_JAR%
    exit /b 1
)

call :find_jar "%~dp0."
if defined JAR goto run

call :find_jar "%~dp0.."
if defined JAR goto run

call :find_jar "%~dp0..\..\..\..\target"
if defined JAR goto run

call :find_jar "%CD%"
if defined JAR goto run

echo [ERROR] Не найден ufos-installer.jar.
echo Положите ufos-installer.jar рядом со скриптом или задайте переменную окружения UFOS_INSTALLER_JAR.
exit /b 1

:run
java -jar "%JAR%" --mode init %*
exit /b %ERRORLEVEL%

:find_jar
set "SEARCH_DIR=%~1"

if not exist "%SEARCH_DIR%\" exit /b 0

if exist "%SEARCH_DIR%\ufos-installer.jar" (
    set "JAR=%SEARCH_DIR%\ufos-installer.jar"
    exit /b 0
)

for %%f in ("%SEARCH_DIR%\ufos-installer*.jar") do (
    set "JAR=%%~ff"
    exit /b 0
)

exit /b 0