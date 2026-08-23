package io.github.neo236.packwarden.companion;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Textos del instalador.
 *
 * <p>Estaban escritos dentro del codigo, lo que ademas de impedir traducirlos
 * obligaba a recompilar para cambiar una palabra. Ahora salen de archivos de
 * idioma: se elige por el idioma del sistema, y se puede forzar con
 * {@code --language}.
 */
public final class Messages {

    private static final String BUNDLE = "packwarden.installer";

    private static ResourceBundle bundle = load(Locale.getDefault());

    private Messages() {}

    /** @param language codigo de idioma, por ejemplo "es" o "en". Vacio = el del sistema. */
    public static void setLanguage(String language) {
        if (language != null && !language.isBlank()) {
            bundle = load(Locale.forLanguageTag(language.trim()));
        }
    }

    public static String get(String key, Object... arguments) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            // Antes que romper la ventana, se muestra la clave: es feo pero visible,
            // y deja claro que falta traducir algo.
            return key;
        }
        return arguments.length == 0 ? pattern : MessageFormat.format(pattern, arguments);
    }

    private static ResourceBundle load(Locale locale) {
        try {
            return ResourceBundle.getBundle(BUNDLE, locale);
        } catch (MissingResourceException e) {
            return ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH);
        }
    }
}
