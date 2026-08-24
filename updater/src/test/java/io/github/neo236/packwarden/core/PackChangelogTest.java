package io.github.neo236.packwarden.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El detalle de cambios que ve el jugador antes de aceptar una actualizacion. */
class PackChangelogTest {

    private static PackManifest.Entry instalado(String hash) {
        return new PackManifest.Entry(hash, "mods/algo.jar", true);
    }

    /** Anotado por packwiz, pero del otro lado: nunca estuvo instalado aca. */
    private static PackManifest.Entry otroLado() {
        return new PackManifest.Entry(null, "mods/otro.pw.toml", false);
    }

    @Test
    @DisplayName("detecta altas, bajas y actualizaciones")
    void diffBasico() {
        Map<String, PackManifest.Entry> local = new LinkedHashMap<>();
        local.put("mods/create.pw.toml", instalado("aaa"));
        local.put("mods/jei.pw.toml", instalado("bbb"));
        local.put("mods/viejo.pw.toml", instalado("ccc"));

        Map<String, String> remoto = new LinkedHashMap<>();
        remoto.put("mods/create.pw.toml", "aaa");
        remoto.put("mods/jei.pw.toml", "DISTINTO");
        remoto.put("mods/nuevo.pw.toml", "ddd");

        PackChangelog cambios = PackChangelog.between(local, remoto);

        assertEquals(1, cambios.added().size());
        assertEquals("Nuevo", cambios.added().get(0));
        assertEquals(1, cambios.removed().size());
        assertEquals("Viejo", cambios.removed().get(0));
        assertEquals(1, cambios.updated().size());
        assertEquals("Jei", cambios.updated().get(0));
        assertEquals(3, cambios.total());
    }

    @Test
    @DisplayName("las entradas del otro lado no cuentan como bajas")
    void sinBajasFantasma() {
        // Este fue un error real: packwiz conserva en el manifiesto las entradas
        // del otro lado aunque el mod desaparezca del pack, y aparecian en pantalla
        // como mods que se quitaban.
        Map<String, PackManifest.Entry> local = new LinkedHashMap<>();
        local.put("mods/create.pw.toml", instalado("aaa"));
        local.put("mods/better-third-person.pw.toml", otroLado());

        Map<String, String> remoto = Map.of("mods/create.pw.toml", "aaa");

        PackChangelog cambios = PackChangelog.between(local, remoto);

        assertTrue(cambios.isEmpty(), "no deberia reportar ningun cambio, y reportaba una baja");
    }

    @Test
    @DisplayName("un indice remoto vacio no inventa cambios")
    void indiceVacio() {
        Map<String, PackManifest.Entry> local = Map.of("mods/create.pw.toml", instalado("aaa"));
        assertTrue(PackChangelog.between(local, Map.of()).isEmpty());
    }

    @Test
    @DisplayName("los nombres se muestran legibles")
    void nombresLegibles() {
        assertEquals("Create Aeronautics", PackChangelog.displayName("mods/create-aeronautics.pw.toml"));
        assertEquals("Fresh Animations", PackChangelog.displayName("resourcepacks/fresh-animations.pw.toml"));
        // Archivos sueltos: hay que sacarles la extension y la version pegada.
        assertEquals("Minetuki Updater", PackChangelog.displayName("mods/minetuki_updater-1.0.0.jar"));
        // Y no romper los que empiezan con digito.
        assertEquals("3dskinlayers", PackChangelog.displayName("mods/3dskinlayers.pw.toml"));
    }
}
