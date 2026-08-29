@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title MineTUKI - Instalador

rem ---------------------------------------------------------------------------
rem Este script se ejecuta con java.exe y NO con javaw.exe, y sin "start".
rem
rem javaw no tiene consola: cuando la JVM no arranca --jar que falta, version
rem de Java demasiado vieja-- el error no aparece en ningun lado. Sumado a
rem "start", que desprende el proceso y deja que el .bat termine enseguida, el
rem sintoma era una ventana negra que se abria y se cerraba sin decir nada.
rem
rem Ahora se espera al proceso, se guarda todo lo que escribe en un registro y,
rem si algo falla, se muestra en pantalla y la ventana no se cierra sola.
rem ---------------------------------------------------------------------------

set "LOG=%~dp0instalacion.log"
(echo.) > "%LOG%" 2>nul
if not exist "%LOG%" set "LOG=%TEMP%\minetuki-instalacion.log"

call :registrar "MineTUKI - registro de instalacion"
call :registrar "fecha: %DATE% %TIME%"
call :registrar "carpeta: %~dp0"

echo.
echo   MineTUKI - Instalador
echo   =====================
echo.

rem --- Los archivos tienen que estar descomprimidos --------------------------
rem
rem Al hacer doble clic en el .bat desde el visor de carpetas comprimidas,
rem Windows extrae SOLO ese archivo a una carpeta temporal. Los .jar no viajan
rem con el, y java moria sin mostrar nada.

set "FALTA="
if not exist "%~dp0packwarden-companion.jar" set "FALTA=packwarden-companion.jar"
if not exist "%~dp0packwiz-installer-bootstrap.jar" set "FALTA=!FALTA! packwiz-installer-bootstrap.jar"

if defined FALTA (
    echo   Falta este archivo, que tiene que estar al lado del instalador:
    echo     !FALTA!
    echo.
    echo   Suele pasar por abrir el .bat desde adentro del zip. Descomprimi la
    echo   carpeta entera a algun lado, por ejemplo el Escritorio, y ejecutalo
    echo   desde ahi.
    call :registrar "ERROR: faltan archivos: !FALTA!"
    echo.
    pause
    exit /b 1
)

rem --- Buscar un Java que sirva ----------------------------------------------
rem
rem No alcanza con encontrar "un" java: el instalador necesita Java 17 o mas
rem nuevo. El launcher de Minecraft guarda varios runtimes a la vez, y entre
rem ellos esta jre-legacy, que es Java 8. Tomar el primero que apareciera hacia
rem que en algunas maquinas se eligiera justo ese y la JVM muriera al instante
rem con UnsupportedClassVersionError.
rem
rem Se prueban todos los candidatos, se lee la version de cada uno y se elige
rem el mas nuevo.

echo   Buscando Java...

set "JAVA_BIN="
set "JAVA_MAJOR=0"
set "PF86=%ProgramFiles(x86)%"
set "PF=%ProgramFiles%"
set "PROBE=%TEMP%\minetuki-java-version.txt"

for %%R in (
    "%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime"
    "%APPDATA%\.minecraft\runtime"
    "%PF86%\Minecraft Launcher\runtime"
    "%PF%\Minecraft Launcher\runtime"
) do (
    if exist %%R (
        for /f "delims=" %%J in ('dir /s /b "%%~R\java.exe" 2^>nul') do call :probar "%%J"
    )
)

if defined JAVA_HOME call :probar "%JAVA_HOME%\bin\java.exe"

for /f "delims=" %%J in ('where java.exe 2^>nul') do call :probar "%%J"

del "%PROBE%" >nul 2>&1

if not defined JAVA_BIN (
    echo   No se encontro Java 17 o mas nuevo en esta computadora.
    echo.
    echo   Si tenes el launcher de Minecraft, abri una vez cualquier version
    echo   1.18 o superior: el launcher descarga Java solo y despues este
    echo   instalador lo encuentra.
    echo.
    echo   Si no, instalalo desde:
    echo     https://adoptium.net/temurin/releases/?version=21
    echo.
    echo   El detalle de lo que se probo quedo en:
    echo     %LOG%
    call :registrar "ERROR: ningun candidato llego a Java 17"
    echo.
    pause
    exit /b 1
)

echo   Java !JAVA_MAJOR! encontrado.
echo.
echo   Abriendo el instalador. No cierres esta ventana.
echo.
call :registrar "elegido: !JAVA_BIN! (Java !JAVA_MAJOR!)"

"!JAVA_BIN!" -jar "%~dp0packwarden-companion.jar" --install ^
    --pack-name "MineTUKI" ^
    --title "NeoTUKI Mod Updater" ^
    --command-alias "tuki" ^
    --fallback-url "https://raw.githubusercontent.com/Neo236/MineTUKI/main/pack.toml" ^
    --pack-url "https://minetuki-neo236s-projects.vercel.app/pack.toml" ^
    --neoforge-version "neoforge-21.1.248" ^
    --folder-name "minetuki" ^
    --bootstrap "%~dp0packwiz-installer-bootstrap.jar" >> "%LOG%" 2>&1

set "CODIGO=%ERRORLEVEL%"
call :registrar "codigo de salida: !CODIGO!"

if not "!CODIGO!"=="0" (
    echo.
    echo   El instalador termino con un error ^(codigo !CODIGO!^).
    echo.
    echo   ------------------------------------------------------------------
    type "%LOG%"
    echo   ------------------------------------------------------------------
    echo.
    echo   Ese mismo texto quedo guardado en:
    echo     %LOG%
    echo   Mandaselo a quien te paso el modpack.
    echo.
    pause
    exit /b !CODIGO!
)

exit /b 0

rem ---------------------------------------------------------------------------
rem Mide la version de un java.exe y se queda con el mas nuevo de los que
rem lleguen a 17.
rem
rem La version se vuelca a un archivo y se lee desde ahi en vez de capturar la
rem salida del comando: las rutas del launcher tienen espacios, y anidar
rem comillas adentro de un for /f es donde eso se rompe.
rem ---------------------------------------------------------------------------
:probar
if not exist %1 goto :eof

"%~1" -version > "%PROBE%" 2>&1
if errorlevel 1 (
    call :registrar "candidato: %~1 : no ejecuta"
    goto :eof
)

set "V="
for /f "usebackq tokens=1,2,3" %%a in ("%PROBE%") do (
    if /i "%%b"=="version" if not defined V set "V=%%c"
)
if not defined V (
    call :registrar "candidato: %~1 : version ilegible"
    goto :eof
)

set "V=!V:"=!"

rem Java 8 se presenta como 1.8.0_x y los demas como 21.0.10; el numero que
rem importa es el segundo en el primer caso y el primero en el resto.
set "M="
for /f "delims=.-_+ tokens=1,2" %%a in ("!V!") do (
    if "%%a"=="1" (set "M=%%b") else (set "M=%%a")
)
if not defined M goto :eof

call :registrar "candidato: %~1 : !V! (Java !M!)"

if !M! LSS 17 goto :eof
if !M! GTR !JAVA_MAJOR! (
    set "JAVA_MAJOR=!M!"
    set "JAVA_BIN=%~1"
)
goto :eof

:registrar
>> "%LOG%" echo %~1
goto :eof
