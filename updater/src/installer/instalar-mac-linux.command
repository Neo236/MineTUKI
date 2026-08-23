#!/bin/bash
cd "$(dirname "$0")" || exit 1

# Busca un Java utilizable antes de pedirle a nadie que instale nada. El
# launcher oficial trae el suyo, asi que en la mayoria de las maquinas ya hay
# uno disponible.

JAVA_BIN=""

if command -v java >/dev/null 2>&1; then
    JAVA_BIN="java"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
else
    for root in \
        "$HOME/Library/Application Support/minecraft/runtime" \
        "$HOME/.minecraft/runtime"
    do
        if [ -d "$root" ]; then
            found=$(find "$root" -name java -type f -perm -u+x 2>/dev/null | head -1)
            if [ -n "$found" ]; then
                JAVA_BIN="$found"
                break
            fi
        fi
    done
fi

if [ -z "$JAVA_BIN" ]; then
    echo
    echo "No se encontro Java en esta computadora."
    echo
    echo "Descargalo desde: https://adoptium.net/temurin/releases/?version=21"
    echo
    read -r -p "Presiona Enter para cerrar..."
    exit 1
fi

"$JAVA_BIN" -jar "$(pwd)/packwarden-companion.jar" --install \
    --pack-name "MineTUKI" \
    --title "NeoTUKI Mod Updater" \
    --command-alias "tuki" \
    --fallback-url "https://raw.githubusercontent.com/Neo236/MineTUKI/main/pack.toml" \
    --pack-url "https://minetuki-neo236s-projects.vercel.app/pack.toml" \
    --neoforge-version "neoforge-21.1.248" \
    --folder-name "minetuki" \
    --bootstrap "$(pwd)/packwiz-installer-bootstrap.jar"
