package io.github.neo236.packwarden.companion;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Ventana del instalador de primera vez.
 *
 * <p>Una sola pantalla, dos opciones y un boton. La version anterior era un .bat que
 * dejaba los mods en una carpeta suelta y un LEEME que terminaba con "de ahi en mas
 * es cosa tuya llevarlos a tu carpeta de mods": justamente el paso donde se caia la
 * gente.
 */
public final class InstallerUi {

    private final String brand;
    private final String packUrl;
    private final String neoForgeVersion;
    private final String folderName;
    private final Path bootstrapJar;

    private JFrame frame;
    private JRadioButton profileOption;
    private JRadioButton customOption;
    private JLabel customPathLabel;
    private JLabel statusLabel;
    private JProgressBar progress;
    private JButton installButton;
    private Path customFolder;

    public InstallerUi(String brand, String packUrl, String neoForgeVersion, String folderName, Path bootstrapJar) {
        this.brand = brand;
        this.packUrl = packUrl;
        this.neoForgeVersion = neoForgeVersion;
        this.folderName = folderName;
        this.bootstrapJar = bootstrapJar;
    }

    public void show() {
        frame = new JFrame(brand);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        content.add(title(brand));
        content.add(Box.createVerticalStrut(4));
        content.add(subtitle("Elegi donde instalar los mods."));
        content.add(Box.createVerticalStrut(14));

        content.add(destinationPanel());
        content.add(Box.createVerticalStrut(12));
        content.add(memoryPanel());
        content.add(Box.createVerticalStrut(14));

        progress = new JProgressBar();
        progress.setVisible(false);
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(progress);

        statusLabel = subtitle(" ");
        content.add(statusLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        installButton = new JButton("Instalar");
        installButton.addActionListener(event -> install());
        buttons.add(installButton);
        content.add(Box.createVerticalStrut(10));
        content.add(buttons);

        frame.setContentPane(content);
        frame.pack();
        frame.setMinimumSize(new Dimension(520, frame.getHeight()));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel destinationPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        boolean hasLauncher = Platform.hasOfficialLauncher();

        profileOption = new JRadioButton("Crear un perfil aparte en el launcher (recomendado)", hasLauncher);
        profileOption.setEnabled(hasLauncher);
        panel.add(profileOption);
        panel.add(hint(hasLauncher
                ? "Instala en una carpeta propia. No toca los mods que ya tengas."
                : "No se encontro el launcher oficial en esta maquina."));

        customOption = new JRadioButton("Elegir una carpeta yo mismo", !hasLauncher);
        panel.add(customOption);
        panel.add(hint("Para Prism, MultiMC u otra instalacion."));

        ButtonGroup group = new ButtonGroup();
        group.add(profileOption);
        group.add(customOption);

        JPanel chooser = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        chooser.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton browse = new JButton("Elegir carpeta...");
        browse.addActionListener(event -> chooseFolder());
        customPathLabel = hint("(ninguna elegida)");
        chooser.add(browse);
        chooser.add(Box.createHorizontalStrut(8));
        chooser.add(customPathLabel);
        panel.add(chooser);

        return panel;
    }

    /** Deja a la vista cuanta memoria se va a asignar, y avisa si la maquina esta justa. */
    private JPanel memoryPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        int totalGb = Platform.totalMemoryGb();
        int heapGb = Platform.recommendedHeapGb();

        panel.add(hint("Memoria detectada: " + (totalGb > 0 ? totalGb + " GB" : "desconocida")
                + "  -  se le asignaran " + heapGb + " GB al juego."));

        if (Platform.isMemoryTight()) {
            JLabel warning = hint("Aviso: este modpack es pesado y con menos de 10 GB puede ir a los tirones.");
            warning.setForeground(new Color(0xB8, 0x6A, 0x00));
            panel.add(warning);
        }
        return panel;
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Elegi la carpeta del juego");
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            customFolder = chooser.getSelectedFile().toPath();
            customPathLabel.setText(customFolder.toString());
            customOption.setSelected(true);
            frame.pack();
        }
    }

    private void install() {
        Installer.Destination destination = profileOption.isSelected()
                ? Installer.Destination.DEDICATED_PROFILE
                : Installer.Destination.CUSTOM_FOLDER;

        Path minecraftFolder = Platform.minecraftFolder();
        Path gameDirectory;

        if (destination == Installer.Destination.DEDICATED_PROFILE) {
            if (!LauncherProfiles.exists(minecraftFolder)) {
                error("No se encontro el launcher oficial.\n\n"
                        + "Abrilo una vez y volve a intentar, o elegi una carpeta a mano.");
                return;
            }
            if (LauncherProfiles.looksLikeLauncherRunning()) {
                int answer = JOptionPane.showConfirmDialog(
                        frame,
                        "Parece que el launcher esta abierto.\n\n"
                                + "Al cerrarse vuelve a escribir su configuracion y podria borrar\n"
                                + "el perfil que estamos por crear. Conviene cerrarlo primero.\n\n"
                                + "Cerralo y despues segui. Continuar igual?",
                        brand,
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            gameDirectory = minecraftFolder.resolve(folderName);
        } else {
            if (customFolder == null) {
                error("Elegi una carpeta primero.");
                return;
            }
            gameDirectory = customFolder;
        }

        Installer.Options options = new Installer.Options(
                destination,
                minecraftFolder,
                gameDirectory,
                packUrl,
                neoForgeVersion,
                brand,
                "packwarden-" + folderName,
                bootstrapJar);

        installButton.setEnabled(false);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        frame.pack();

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                Installer.run(options, this::publish);
                return null;
            }

            @Override
            protected void process(java.util.List<String> messages) {
                statusLabel.setText(messages.get(messages.size() - 1));
            }

            @Override
            protected void done() {
                progress.setIndeterminate(false);
                progress.setVisible(false);
                installButton.setEnabled(true);
                try {
                    get();
                    finished(destination, gameDirectory);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    statusLabel.setText("No se pudo completar.");
                    error("La instalacion no se completo.\n\n" + cause.getMessage());
                }
            }
        }.execute();
    }

    private void finished(Installer.Destination destination, Path gameDirectory) {
        String message = destination == Installer.Destination.DEDICATED_PROFILE
                ? "Listo.\n\nAbri el launcher y elegi el perfil \"" + brand + "\".\n"
                        + "De ahora en mas el juego te avisa solo cuando haya cambios."
                : "Listo.\n\nLos mods quedaron en:\n" + gameDirectory
                        + "\n\nApunta tu instancia a esa carpeta.";
        statusLabel.setText("Instalacion completa.");
        JOptionPane.showMessageDialog(frame, message, brand, JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(frame, message, brand, JOptionPane.ERROR_MESSAGE);
    }

    private static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() + 5f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel subtitle(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, label.getFont().getSize() - 1f));
        label.setForeground(Color.GRAY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 22, 4, 0));
        return label;
    }

    /** Punto de entrada del modo instalador. */
    public static void launch(
            String brand, String packUrl, String neoForgeVersion, String folderName, String bootstrap) {
        Path bootstrapJar = Paths.get(bootstrap);
        if (!Files.isRegularFile(bootstrapJar)) {
            JOptionPane.showMessageDialog(
                    null,
                    "Falta el instalador de packwiz:\n" + bootstrapJar,
                    brand,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(
                () -> new InstallerUi(brand, packUrl, neoForgeVersion, folderName, bootstrapJar).show());
    }
}
