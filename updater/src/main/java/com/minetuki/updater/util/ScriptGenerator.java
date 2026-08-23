package com.minetuki.updater.util;

import com.minetuki.updater.MinetukiUpdater;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.AlertScreen;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public class ScriptGenerator {

    private static final String PACKWIZ_URL = "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar";
    private static final String REMOTE_PACK_URL = "https://raw.githubusercontent.com/Neo236/MineTUKI/main/pack.toml";

    public static void executeUpdateAndShutdown(net.minecraft.client.gui.screens.Screen previousScreen) {
        try {
            Path bootstrapPath = Paths.get("packwiz-installer-bootstrap.jar");
            if (!Files.exists(bootstrapPath)) {
                MinetukiUpdater.LOGGER.info("Descargando packwiz-installer-bootstrap...");
                HttpURLConnection conn = (HttpURLConnection) new URL(PACKWIZ_URL).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                try (InputStream in = conn.getInputStream()) {
                    Files.copy(in, bootstrapPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + "/bin/java";
            
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            boolean isWindows = os.contains("win");
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            if (isWindows) {
                javaBin = javaBin + ".exe";
                String scriptName = "update_minetuki_" + timestamp + ".bat";
                Path scriptPath = Paths.get(scriptName);
                String batContent = "@echo off\r\n" +
                        "title MineTUKI Updater\r\n" +
                        "echo ==========================================\r\n" +
                        "echo      ACTUALIZANDO MODPACK MINETUKI\r\n" +
                        "echo ==========================================\r\n" +
                        "echo Esperando a que Minecraft se cierre completamente...\r\n" +
                        "timeout /t 3 /nobreak > nul\r\n" +
                        "echo.\r\n" +
                        "echo Descargando e instalando actualizaciones...\r\n" +
                        "\"" + javaBin + "\" -jar packwiz-installer-bootstrap.jar " + REMOTE_PACK_URL + "\r\n" +
                        "echo.\r\n" +
                        "echo ==========================================\r\n" +
                        "echo ACTUALIZACION COMPLETADA.\r\n" +
                        "echo Mostrando ventana emergente...\r\n" +
                        "powershell -Command \"Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.MessageBox]::Show('Instalacion lista, puedes iniciar el juego.', 'MineTUKI Updater', 'OK', 'Information')\"\r\n" +
                        "del \"%~f0\"";
                Files.write(scriptPath, batContent.getBytes());
                
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/c", scriptName);
                pb.directory(Paths.get("").toFile());
                pb.start();
            } else {
                String scriptName = "update_minetuki_" + timestamp + ".sh";
                Path scriptPath = Paths.get(scriptName);
                String shContent = "#!/bin/bash\n" +
                        "echo 'Esperando a que Minecraft se cierre...'\n" +
                        "sleep 3\n" +
                        "echo 'Actualizando...'\n" +
                        "\"" + javaBin + "\" -jar packwiz-installer-bootstrap.jar " + REMOTE_PACK_URL + "\n" +
                        "if [ \"$(uname)\" = \"Darwin\" ]; then\n" +
                        "  osascript -e 'display alert \"MineTUKI Updater\" message \"Instalacion lista, puedes iniciar el juego.\"' \n" +
                        "elif command -v zenity &> /dev/null; then\n" +
                        "  zenity --info --title='MineTUKI Updater' --text='Instalacion lista, puedes iniciar el juego.'\n" +
                        "elif command -v kdialog &> /dev/null; then\n" +
                        "  kdialog --msgbox 'Instalacion lista, puedes iniciar el juego.' --title 'MineTUKI Updater'\n" +
                        "else\n" +
                        "  echo 'Instalacion completada. Presiona ENTER para salir.'\n" +
                        "  read\n" +
                        "fi\n" +
                        "rm -- \"$0\"\n";
                Files.write(scriptPath, shContent.getBytes());
                
                ProcessBuilder chmod = new ProcessBuilder("chmod", "+x", scriptName);
                chmod.start().waitFor();
                
                ProcessBuilder pb = new ProcessBuilder("x-terminal-emulator", "-e", "./" + scriptName);
                pb.directory(Paths.get("").toFile());
                try {
                    pb.start();
                } catch (Exception e) {
                    new ProcessBuilder("sh", "-c", "gnome-terminal -- ./" + scriptName + " || konsole -e ./" + scriptName).start();
                }
            }

            MinetukiUpdater.LOGGER.info("Script de actualizacion lanzado. Cerrando Minecraft...");
            System.exit(0);

        } catch (Exception e) {
            MinetukiUpdater.LOGGER.error("Error al generar script de actualizacion: " + e.getMessage(), e);
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(new AlertScreen(
                    () -> Minecraft.getInstance().setScreen(previousScreen),
                    Component.literal("Error de Actualizacion"),
                    Component.literal("No se pudo iniciar la actualizacion. Razon: " + e.getMessage())
                ));
            });
        }
    }
}
