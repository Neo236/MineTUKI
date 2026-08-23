package com.minetuki.updater.network;

import com.minetuki.updater.MinetukiUpdater;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    public static final String REMOTE_PACK_URL = "https://raw.githubusercontent.com/Neo236/MineTUKI/main/pack.toml";
    
    public static boolean checkForUpdates() {
        MinetukiUpdater.LOGGER.info("Chequeando actualizaciones en GitHub...");
        try {
            URL url = new URL(REMOTE_PACK_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            if (connection.getResponseCode() != 200) {
                MinetukiUpdater.LOGGER.warn("No se pudo conectar a GitHub para revisar actualizaciones (Código: " + connection.getResponseCode() + ").");
                return false;
            }
            
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine).append("\n");
            }
            in.close();
            
            String remoteHash = extractHash(content.toString());
            if (remoteHash == null) {
                MinetukiUpdater.LOGGER.warn("No se encontro el hash en el archivo pack.toml remoto.");
                return false;
            }
            
            Path localPackPath = Paths.get("pack.toml");
            if (!Files.exists(localPackPath)) {
                MinetukiUpdater.LOGGER.info("No se encontro pack.toml local. Se requiere actualizacion.");
                return true;
            }
            
            String localContent = new String(Files.readAllBytes(localPackPath));
            String localHash = extractHash(localContent);
            
            if (localHash == null) {
                MinetukiUpdater.LOGGER.info("No se encontro hash en el pack.toml local. Se requiere actualizacion.");
                return true;
            }
            
            if (!remoteHash.equals(localHash)) {
                MinetukiUpdater.LOGGER.info("¡Nueva actualizacion encontrada! (Local: " + localHash + ", Remoto: " + remoteHash + ")");
                return true;
            } else {
                MinetukiUpdater.LOGGER.info("El modpack esta actualizado.");
                return false;
            }
            
        } catch (Exception e) {
            MinetukiUpdater.LOGGER.error("Excepcion al buscar actualizaciones: " + e.getMessage());
            return false;
        }
    }
    
    private static String extractHash(String tomlContent) {
        Pattern pattern = Pattern.compile("hash\\s*=\\s*\"([a-fA-F0-9]+)\"");
        Matcher matcher = pattern.matcher(tomlContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
