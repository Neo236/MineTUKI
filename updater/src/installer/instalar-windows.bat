@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title MineTUKI - Instalador

rem Busca un Java utilizable. La mayoria de la gente ya tiene uno sin saberlo:
rem el launcher oficial descarga el suyo. Pedirle a alguien que instale Java
rem era el punto donde se caia media instalacion.

set "JAVA_BIN="

rem 1. El que este en el PATH.
where javaw.exe >nul 2>&1 && set "JAVA_BIN=javaw.exe"

rem 2. JAVA_HOME.
if not defined JAVA_BIN if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA_BIN=%JAVA_HOME%\bin\javaw.exe"
)

rem 3. Los runtimes del launcher oficial, en sus dos ubicaciones posibles.
if not defined JAVA_BIN (
    for %%R in (
        "%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime"
        "%APPDATA%\.minecraft\runtime"
        "%ProgramFiles(x86)%\Minecraft Launcher\runtime"
    ) do (
        if not defined JAVA_BIN (
            if exist %%R (
                for /f "delims=" %%J in ('dir /s /b "%%~R\javaw.exe" 2^>nul') do (
                    if not defined JAVA_BIN set "JAVA_BIN=%%J"
                )
            )
        )
    )
)

if not defined JAVA_BIN (
    echo.
    echo No se encontro Java en esta computadora.
    echo.
    echo Descargalo desde: https://adoptium.net/temurin/releases/?version=21
    echo.
    pause
    exit /b 1
)

start "" "!JAVA_BIN!" -jar "%~dp0packwarden-companion.jar" --install ^
    --pack-name "MineTUKI" ^
    --title "NeoTUKI Mod Updater" ^
    --command-alias "tuki" ^
    --fallback-url "https://raw.githubusercontent.com/Neo236/MineTUKI/main/pack.toml" ^
    --pack-url "https://minetuki-neo236s-projects.vercel.app/pack.toml" ^
    --neoforge-version "neoforge-21.1.248" ^
    --folder-name "minetuki" ^
    --bootstrap "%~dp0packwiz-installer-bootstrap.jar"
