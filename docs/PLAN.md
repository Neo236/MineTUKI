# Plan técnico — MineTUKI

Estado: **decisiones cerradas**, ejecución en curso.
Última actualización: 2026-08-23.

## 1. Objetivo

Tres cosas, en orden de importancia:

1. Una **lista de mods** que cambie con el tiempo sin romper a nadie.
2. Un **instalador** que deje al jugador listo para jugar, no listo para copiar archivos a mano.
3. Un **mod** que haga lo mismo que el instalador pero dentro de Minecraft, detectando
   cambios solo y actualizando con la menor fricción posible — en cliente y en servidor.

El mod se diseña **genérico y reusable** en cualquier servidor futuro. La marca NeoTUKI
entra por configuración, nunca por código.

## 2. Decisiones

| Tema | Decisión |
|---|---|
| modid | `packwarden` |
| Nombre visible | NeoTUKI's Mod Updater (configurable) |
| Comando | `/packwarden`, alias configurable → `/tuki` |
| Repos | **Monorepo**: el mod vive en `updater/` dentro de este repo |
| Distribución del mod | Un solo jar, con guardas de `Dist`. `side = "client"` hasta que exista la parte servidor; `both` a partir de la Fase 2 |
| Destinos del instalador | **Perfil dedicado** (default) y **Ubicación personalizada** |
| `.minecraft` como destino | **Descartado** como opción propia |
| Fuente del pack (cliente) | Vercel |
| Fuente del pack (servidor) | GitHub raw |
| IP del servidor | **No se publica.** El instalador la pide como campo opcional |
| i18n | Claves de idioma con placeholders desde el día uno |

### Por qué se descartó `.minecraft` como destino

Se había incluido como "solución universal" para launchers pirata. No aplica: el servidor
corre con `online-mode=true` y whitelist, así que un launcher pirata no puede entrar aunque
tenga los mods perfectos.

El riesgo que quedaba era real pero distinto del que se temía. packwiz **no borra archivos
ajenos** — lleva un manifiesto de lo que instaló y solo toca eso. El problema es que
`.minecraft` es **compartida entre todos los perfiles**: los 151 mods de MineTUKI conviven
con los de cualquier otro modpack del jugador y el juego no arranca.

"Ubicación personalizada" cubre Prism/MultiMC y cubre `.minecraft` para quien insista, pero
eligiéndola a mano en vez de que se la ofrezcamos.

## 3. Arquitectura

```
                    ┌──────────────────────────┐
                    │  Repo MineTUKI (GitHub)  │
                    │  pack.toml + index.toml  │
                    │  mods/*.pw.toml          │
                    │  updater/  (código)      │
                    └────────┬─────────┬───────┘
                             │         │
                  push → deploy        │ raw
                             │         │
                    ┌────────▼─────┐  ┌▼────────────────┐
                    │  Vercel      │  │ raw.github...   │
                    │  instantáneo │  │ hasta 5 min     │
                    └────────┬─────┘  └┬────────────────┘
                             │         │
                    ┌────────▼───┐  ┌──▼──────────────┐
                    │  Clientes  │  │ Servidor Docker │
                    │  (mod)     │  │ (itzg packwiz)  │
                    └────────────┘  └─────────────────┘
```

**Por qué el servidor no usa Vercel.** El servidor solo sincroniza al reiniciar, así que 5
minutos de retraso le dan igual. Y como el arranque **falla duro** si la instalación de
packwiz falla (ver §4), cada dependencia en ese camino es un modo de falla nuevo. El cliente
sí va por Vercel, donde la inmediatez importa, y lleva la URL de GitHub como fallback
configurable — algo que itzg no tiene.

### Componentes

**`updater/` — el mod (`packwarden`)**
Un jar, los dos lados. Toda la configuración en TOML: URL del pack, URL de fallback, nombre
visible, alias del comando, intervalos, política de votación.

**Companion jar** — un solo binario con dos modos, embebido en el mod y distribuido también
como zip:

- `--install` → GUI de primera instalación.
- `--update` → GUI de progreso, corre cuando Minecraft ya se cerró.

Reemplaza los `.bat`/`.sh` generados en runtime, que hoy son el punto más frágil: dependen de
`x-terminal-emulator`/`gnome-terminal`/`konsole` en Linux y son carne de antivirus en Windows.
Además **embebe `packwiz-installer-bootstrap.jar`** (99 KB), eliminando una descarga en
runtime y su punto de falla.

## 4. Hechos verificados

Todo lo que sigue se midió o se leyó del código, no se asumió.

| Hecho | Verificación |
|---|---|
| El pack publicado estaba roto | El instalador publicado abortaba con `[FATAL] Your index file hash is invalid!` |
| El arreglo era de una línea | `packwiz refresh` dejó un diff de 1 línea en `pack.toml` |
| packwiz guarda su estado en `packwiz.json` | Constante hallada en `Main.class` del instalador |
| Ese manifiesto expone `packFileHash`, `indexFileHash`, `cachedSide`, `cachedFiles` | Inspección de `ManifestFile.class` |
| Flags reales del instalador: `pack-folder`, `side`, `no-gui`, `title`, `multimc-folder`, `timeout` | Constantes en `Main.class` + ejecución real |
| El instalador devuelve exit code 1 al fallar | Medido |
| itzg corre `Main -s server $PACKWIZ_URL` en **cada arranque** | Fuente: `scripts/start-setupModpack` |
| Si packwiz falla, itzg hace `exit 1` y el contenedor no arranca | Misma fuente |
| NeoForge soporta instalación headless: `--install-client [File]` | `--help` del instalador 21.1.248 |
| `launcher_profiles.json` soporta `javaArgs` y `gameDir` | Inspección del archivo real |
| Hay **un solo** `options.txt` y **un solo** `servers.dat`, en la raíz de `.minecraft` | Inspección del filesystem |
| El launcher oficial trae su propio Java 21 | `java-runtime-delta` → OpenJDK 21.0.7 |
| GitHub raw tarda ~4 min en propagar, **por nodo de edge** | Medido: 249 s; otro edge seguía viejo a los 8 min |
| Vercel sirve el pack completo correctamente | Instalación real de 100 mods desde la URL de Vercel |
| Vercel ya trae los headers correctos sin configurar nada | `Cache-Control: public, max-age=0, must-revalidate` + ETag |
| Cualquier archivo nuevo del repo entra al índice y se le descarga a todos los clientes | Medido: al crear `docs/PLAN.md`, `packwiz refresh` lo agregó al índice |
| packwiz **no** guarda `pack.toml` ni `index.toml` en la carpeta de juego, solo `packwiz.json` | Inspección del servidor real: el manifiesto está, los otros dos no existen |
| El manifiesto registra **las 153 entradas**, incluidas las de otro lado, con `cachedLocation` nulo | Manifiesto real del servidor |
| El filtrado por `side` funciona en producción | Log del servidor: `(83/153) Skipped MineTUKI Updater (wrong side)` |
| itzg resincroniza en cada arranque y deja el índice al día | Reinicio real: `indexFileHash` pasó de `6e0ceafc` a `0d25b6cd` |
| El loop de reinicio por fallo de packwiz **ocurre de verdad** | Log del servidor: 6 arranques fallidos seguidos antes de uno exitoso |

### Consecuencia operativa importante

Como itzg hace `exit 1` cuando packwiz falla, y el compose usa `restart: unless-stopped`,
**un pack roto produce un loop de reinicio del servidor**. Con el hash desincronizado, un
`docker compose restart mc` habría dejado el servidor caído. Esto convierte el gate de CI de
la Fase 0 en un requisito de disponibilidad, no en una prolijidad.

El mecanismo del loop dejo de ser una prediccion: el 22 de agosto el servidor reintento
**seis veces** antes de levantar, con este error:

```
[init] [ERROR] Failed to get packwiz installer
java.net.UnknownHostException: Failed to resolve 'maven.packwiz.infra.link'
```

Vale la pena saber que, antes de mirar el `PACKWIZ_URL`, itzg descarga el propio instalador
de packwiz desde un Maven de terceros en cada arranque, sin forma de saltearlo.

Pero ese episodio **no fue un problema de configuracion y no requiere arreglo**: los fallos
ocurrieron entre 69 y 114 segundos despues del arranque en frio del host, y el intento
exitoso llego a los 121 segundos. Fue una carrera de arranque, con el contenedor levantando
antes que la red. La politica de reinicio hizo exactamente lo que tenia que hacer.

Queda como evidencia de que el loop es real, y por lo tanto de que el gate de CI importa:
un pack roto si seria un fallo permanente, sin nada que se resuelva solo.

## 5. Fases

### Fase 0 — Desbloquear ✅ hecha

- [x] Sincronizar el hash de `pack.toml` con `index.toml` (commit `38e15f2`).
- [x] Verificar propagación y que el pack quede instalable por ambas URLs.
- [x] Crear el proyecto de Vercel linkeado al repo, deploy en producción.
- [x] Agregar `docs/` y `updater/` a `.packwizignore` para que no se le descarguen a los
      clientes como si fueran contenido del pack.
- [x] Hook `pre-commit` + gate de CI: si `pack.toml` e `index.toml` no coinciden, falla.
- [ ] Empezar a bumpear `version` en `pack.toml` con semver por cada cambio del modlist.

> Publicar una version nueva del mod es ahora empujar una etiqueta `packwarden-vX.Y.Z`:
> el workflow compila, publica el release con el jar y el instalador, reapunta el metadato
> del pack con el hash del artefacto recien compilado, corre `packwiz refresh` y commitea.

### Fase 1 — Higiene

- [x] Mover el código del mod a `updater/` en este repo.
- [x] Sacar el jar del updater del índice como **archivo crudo**. Las entradas crudas de
      `index.toml` no soportan `side`, y por eso hoy el servidor se descarga un mod de GUI.
      Pasa a publicarse como asset de release y se referencia con su propio `.pw.toml`, con
      `side = "client"` mientras el mod no tenga parte servidor. Pasa a `both` en la Fase 2.
      El **comportamiento no cambia** en esta fase, solo el empaquetado y la metadata
      (versión 1.0.0 → 1.0.1). Sacarlo del pack dejaría sin botón a quienes ya lo tienen
      instalado, así que sigue publicado hasta que la Fase 2 lo reemplace.
      Verificado de los dos lados contra un pack de prueba local: con `-s server` el
      instalador responde `Skipped MineTUKI Updater (wrong side)`, y con `-s client` lo baja.
- [ ] Limpiar los 275 MB de Gradle (`gradle.zip`, `gradle-8.9/`).
- [x] Reemplazar la metadata de ejemplo del MDK: `neoforge.mods.toml` todavía dice
      "Example mod description" y viaja un `assets/examplemod/` dentro del jar.
- [ ] Resolver `MineTUKI_test`: borrarlo o agregarle `mods/*.jar` al `.gitignore` (hoy tiene
      141 jars sin trackear ni ignorar).

### Fase 2 — El mod ✅ escrita, parcialmente verificada

Probado contra el servidor real: el mod carga en dedicado sin arrastrar clases de cliente,
la deteccion acierta en los dos sentidos, el alias `/tuki` funciona y el branding sale de la
configuracion. Dos bugs salieron de ejecutarlo, no de leerlo, y estan corregidos: el comando
no le respondia a la consola, y un `check` de solo lectura llegaba a apagar el servidor.

**Falta ejecutar la interfaz del cliente y probar una conexion real** con el handshake
activo. Es lo unico que no se puede verificar por SSH.

**Detección.** Hoy busca `pack.toml` con una ruta relativa al directorio de trabajo, que en el
cliente casi nunca existe, así que devuelve "hay update" **siempre**. Se reemplaza por leer
`packwiz.json` del game dir y comparar su `indexFileHash` contra el `[index] hash` del
`pack.toml` remoto, con cadena de fallback si el manifiesto no está.

**Changelog.** Diff entre `cachedFiles` del manifiesto y el `index.toml` remoto, con nombres.
La diferencia entre "hay una actualización" y una pantalla que da confianza.

**Cliente — tres salidas, no dos.** Hoy el diálogo es Sí/No y el "No" no significa nada
persistente:

- **Actualizar ahora** — cierra y actualiza.
- **Actualizar al salir** — seguís jugando; al cerrar el juego se actualiza solo.
- **Más tarde** — con casilla de *no volver a avisarme por esta versión*, guardada contra el
  hash del índice: cuando salga la siguiente, vuelve a preguntar.

Más `prompt_on_startup` en la config, y un tilde en el botón del zorro cuando hay una
actualización agendada para la salida.

**Espera por PID.** El script actual duerme 3 segundos a ciegas esperando que Minecraft cierre.
Con 151 mods a veces no alcanza, y si el jar sigue bloqueado por la JVM la actualización falla
en Windows. El companion espera al PID del juego.

**Servidor.**

- Chequea cada N minutos (configurable). Si hay cambio, **siempre anuncia**.
- **Sin jugadores** → guarda el mundo, anuncia y apaga. Docker lo levanta e itzg resincroniza.
  El mod no descarga nada: sería duplicar un mecanismo que ya funciona.
- **Con jugadores** → cuenta regresiva y **votación por botones clicables en el chat**
  (`[Postergar 15 min]` / `[Actualizar ya]`). Mayoría simple de los conectados, ventana de voto
  y tope de postergaciones configurables. Kick con mensaje claro antes de apagar.
- `/packwarden update` para ops, que fuerza el ciclo.

**Handshake de versión al conectar.** El servidor manda su `indexFileHash` en el login y el
cliente compara. Limitación honesta: NeoForge ya rechaza conexiones cuando las listas de mods
no coinciden, y su chequeo corre **antes** que el nuestro. El handshake agrega valor cuando las
listas son compatibles pero el pack difiere; para el caso "le falta un mod entero", lo que
podemos mejorar es el mensaje de kick.

**Higiene de concurrencia.** `updateRequired` pasa a `volatile` y el re-chequeo del botón sale
del hilo de render.

### Fase 3 — Instalador 2.0 ✅ hecha

Publicado en `packwarden-v2.0.0` junto con el mod. Verificado en esta maquina: el `.bat`
encuentra el Java del launcher sin que haga falta instalar nada, la ventana detecta el
launcher oficial y muestra la memoria real, y el registro del perfil se probo **contra una
copia del `launcher_profiles.json` real**: agrega el perfil, no pierde ninguno de los seis
existentes, deja uno ajeno byte a byte identico, conserva las claves de raiz, deja respaldo
y es idempotente.

La migracion tambien se probo de punta a punta: una carpeta con el jar viejo instalado queda,
despues de actualizar, con un unico archivo — el nuevo.

Dos destinos, con el primero como default:

1. **Perfil MineTUKI** — crea `.minecraft/minetuki/` como game dir propio, instala mods y
   resourcepacks, siembra `options.txt` con los packs **activados** (bajarlos no los activa),
   instala NeoForge headless si falta, y registra el perfil.
2. **Ubicación personalizada** — el jugador elige la carpeta.

Además:

- **Dimensionado automático de RAM.** Sin `javaArgs` propios, el launcher asigna su default
  (~2 GB), y 2 GB con 151 mods y Distant Horizons es un crash garantizado. El instalador lee
  la RAM real de la máquina y calcula (≈50-60% del total, piso 4 GB, techo 8 GB). *El default
  exacto del launcher conviene confirmarlo al implementar; el dimensionado no depende de él.*
- **Búsqueda de Java en cascada**: `JAVA_HOME` → `PATH` → runtimes del launcher → link a
  Temurin como último recurso. Elimina el requisito de "instalá Java 21" para casi todos.
- **IP opcional**: si el jugador la pega, se siembra `servers.dat`; si no, no se toca el
  archivo. Cero IP en el repo.
- **Migración** de quienes ya instalaron a la vieja usanza, con mods sueltos en
  `.minecraft/mods`.
- Detección de launcher abierto y backup de `launcher_profiles.json` antes de escribirlo.

**Publicación y transición.** El release `client-installer-v1` queda obsoleto pero sigue
funcionando: deja los mods en `modsTUKI` para copiar a mano. Se publica un release nuevo y se
reescribe el `README.md`, que hoy documenta ese flujo viejo y apunta al `pack.toml` de GitHub
raw en vez del de Vercel. Al viejo se le edita la descripción para mandar al nuevo.

**Cuidado con los nombres de archivo.** Uno de los resourcepacks se llama
`-1.21.2 Fresh Moves v3.1 (With Animated Eyes).zip`: empieza con guión y tiene espacios y
paréntesis. `options.txt` se genera con escapado correcto, no concatenando strings.

### Fase 4 — CI/CD ✅ hecha

```
tocás updater/
  → CI buildea el jar
  → lo publica como asset de release
  → actualiza mods/packwarden.pw.toml (versión + hash)
  → packwiz refresh
  → commit + push
  → Vercel redeploya
  → los clientes lo ven al abrir el juego
```

Con una sutileza: **el que ejecuta la actualización es el mod viejo**; el nuevo manda recién en
el arranque siguiente. Funciona porque el juego está cerrado mientras se reemplaza el jar.
Implica que un cambio de protocolo del handshake deja cliente y servidor desfasados un ciclo,
así que **el protocolo se versiona aparte y degrada elegante**.

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| Pack roto → itzg `exit 1` → loop de reinicio | Gate de CI que impide publicar un pack inconsistente |
| El launcher reescribe `launcher_profiles.json` al cerrarse | Detectar launcher abierto, pedir que lo cierre, backup previo |
| Parseo de `launcher_profiles.json` rompe todos los perfiles | Backup `.bak` + parser real, nunca regex |
| El jar del mod está bloqueado en Windows durante el update | Actualizar con el juego cerrado, esperando por PID |
| Desfase de protocolo cliente/servidor | Versionar el protocolo, degradar sin romper |
| Quien pueda pushear a `main` ejecuta código en las máquinas de los amigos | 2FA, branch protection, `GITHUB_TOKEN` de scope mínimo en CI |
| Vercel caído deja sin chequeo a los clientes | Fallback a GitHub raw en la config del mod |
| Un CDN comprometido sirve un mod alterado | Ya cubierto: cada mod está pinneado por sha512 en su `.pw.toml` |
| Un archivo nuevo en el repo termina descargándose a todos los clientes | Todo lo que no es contenido del pack va en `.packwizignore` |
| Finales de línea CRLF ensucian diffs y hashes | `.gitattributes` usa `* -text`; escribir siempre LF y verificar antes de commitear |

## 7. Cómo se verifica cada cambio

El pack es de producción: cuando algo se rompe, se rompe para todos a la vez y el servidor
puede quedar en loop de reinicio. Así que:

1. **Nada se da por sentado.** Cada afirmación de la §4 salió de correr algo o de leer el
   código. Lo que no se pudo verificar queda marcado como tal en el texto.
2. **Toda publicación se prueba contra la URL real**, no contra los archivos locales:
   `packwiz-installer` apuntado a la URL desplegada, en una carpeta descartable. Si el índice
   valida y empieza a bajar mods, el pack está sano.
3. **El gate de CI corre antes que el push**, no después. Un `pack.toml` inconsistente no
   debería poder llegar a `main`.

## 8. Puntos abiertos

- **Licencia.** Hoy es *All Rights Reserved*. Si algún día querés que alguien más lo use, hay
  que cambiarla — nadie adopta un mod que legalmente no puede redistribuir.
- **Fabric.** Es donde está la mayoría de la gente, pero significa Architectury o dos builds.
  Se deja para cuando exista la necesidad; mientras tanto, no hardcodear nada que lo impida.
- **Dominio propio** en Vercel, en lugar de `minetuki-neo236s-projects.vercel.app`.
