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
rem   1. каталог, где лежит сам скрипт;
rem   2. родительский каталог;
rem   3. каталог ..\..\..\..\target, удобно при запуске из исходников;
rem   4. текущий рабочий каталог.
rem

set "JAR="

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java не найдена в PATH. Требуется Java 17+.
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
echo Положите ufos-installer.jar рядом со скриптом или в родительский каталог.
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