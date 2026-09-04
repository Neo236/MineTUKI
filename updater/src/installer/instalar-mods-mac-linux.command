#!/bin/bash
# ---------------------------------------------------------------------------
# Mismo criterio que el .bat de Windows: no salir nunca sin decir por que.
#
# Se guarda todo lo que escribe el instalador en un registro al lado de este
# archivo, y si algo falla se muestra en pantalla y la ventana espera antes de
# cerrarse. De lo contrario un fallo temprano se ve como una terminal que se
# abre y se cierra sola.
# ---------------------------------------------------------------------------
cd "$(dirname "$0")" || exit 1

LOG="$(pwd)/instalacion.log"
: > "$LOG" 2>/dev/null || LOG="${TMPDIR:-/tmp}/minetuki-instalacion.log"

registrar() { echo "$1" >> "$LOG"; }
fin_con_error() {
    echo
    echo "  El detalle quedo en:"
    echo "    $LOG"
    echo
    read -r -p "  Presiona Enter para cerrar..."
    exit 1
}

registrar "MineTUKI - registro de instalacion"
registrar "fecha: $(date)"
registrar "carpeta: $(pwd)"

echo
echo "  MineTUKI - Instalador de mods"
echo "  ============================"
echo

# --- Los archivos tienen que estar descomprimidos --------------------------
falta=""
for archivo in packwarden-companion.jar packwiz-installer-bootstrap.jar; do
    [ -f "$archivo" ] || falta="$falta $archivo"
done
if [ -n "$falta" ]; then
    echo "  Falta este archivo, que tiene que estar al lado del instalador:"
    echo "   $falta"
    echo
    echo "  Descomprimi la carpeta entera a algun lado y ejecutalo desde ahi."
    registrar "ERROR: faltan archivos:$falta"
    fin_con_error
fi

# --- Buscar un Java que sirva ----------------------------------------------
#
# No alcanza con encontrar "un" java: hacen falta 17 o mas. El launcher guarda
# varios runtimes a la vez y entre ellos esta jre-legacy, que es Java 8.
# Quedarse con el primero que apareciera hacia que en algunas maquinas se
# eligiera justo ese y la JVM muriera al instante.

echo "  Buscando Java..."

JAVA_BIN=""
JAVA_MAJOR=0

version_mayor() {
    # Java 8 se presenta como 1.8.0_x y los demas como 21.0.7; el numero que
    # importa es el segundo en el primer caso y el primero en el resto.
    local salida version
    salida=$("$1" -version 2>&1) || return 1
    version=$(printf '%s\n' "$salida" | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -1)
    [ -n "$version" ] || return 1
    case "$version" in
        1.*) printf '%s\n' "$version" | cut -d. -f2 ;;
        *)   printf '%s\n' "$version" | cut -d. -f1 | cut -d- -f1 ;;
    esac
}

probar() {
    [ -x "$1" ] || return 0
    local mayor
    mayor=$(version_mayor "$1") || { registrar "candidato: $1 : no ejecuta"; return 0; }
    case "$mayor" in
        ''|*[!0-9]*) registrar "candidato: $1 : version ilegible"; return 0 ;;
    esac
    registrar "candidato: $1 : Java $mayor"
    if [ "$mayor" -ge 17 ] && [ "$mayor" -gt "$JAVA_MAJOR" ]; then
        JAVA_MAJOR="$mayor"
        JAVA_BIN="$1"
    fi
}

for root in \
    "$HOME/Library/Application Support/minecraft/runtime" \
    "$HOME/.minecraft/runtime"
do
    [ -d "$root" ] || continue
    while IFS= read -r encontrado; do
        probar "$encontrado"
    done < <(find "$root" -name java -type f -perm -u+x 2>/dev/null)
done

[ -n "$JAVA_HOME" ] && probar "$JAVA_HOME/bin/java"
command -v java >/dev/null 2>&1 && probar "$(command -v java)"

if [ -z "$JAVA_BIN" ]; then
    echo "  No se encontro Java 17 o mas nuevo en esta computadora."
    echo
    echo "  Si tenes el launcher de Minecraft, abri una vez cualquier version"
    echo "  1.18 o superior: el launcher descarga Java solo."
    echo
    echo "  Si no, instalalo desde:"
    echo "    https://adoptium.net/temurin/releases/?version=21"
    registrar "ERROR: ningun candidato llego a Java 17"
    fin_con_error
fi

echo "  Java $JAVA_MAJOR encontrado."
echo
echo "  Abriendo el instalador. No cierres esta ventana."
echo
registrar "elegido: $JAVA_BIN (Java $JAVA_MAJOR)"

"$JAVA_BIN" -jar "$(pwd)/packwarden-companion.jar" --install \
    --pack-name "MineTUKI" \
    --title "NeoTUKI Mod Updater" \
    --command-alias "tuki" \
    --fallback-url "https://raw.githubusercontent.com/Neo236/MineTUKI/main/pack.toml" \
    --pack-url "https://minetuki-neo236s-projects.vercel.app/pack.toml" \
    --neoforge-version "neoforge-21.1.248" \
    --folder-name "minetuki" \
    --bootstrap "$(pwd)/packwiz-installer-bootstrap.jar" >> "$LOG" 2>&1

codigo=$?
registrar "codigo de salida: $codigo"

if [ "$codigo" -ne 0 ]; then
    echo
    echo "  El instalador termino con un error (codigo $codigo)."
    echo
    echo "  ------------------------------------------------------------------"
    cat "$LOG"
    echo "  ------------------------------------------------------------------"
    echo
    echo "  Mandaselo a quien te paso el modpack."
    fin_con_error
fi

exit 0
