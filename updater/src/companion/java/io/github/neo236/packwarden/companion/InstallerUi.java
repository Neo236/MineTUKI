package io.github.neo236.packwarden.companion;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
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
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Ventana del instalador de primera vez.
 *
 * <p>Una sola pantalla. La carpeta se puede cambiar en cualquiera de los dos
 * modos: crear el perfil del launcher no obliga a instalar en el lugar por
 * defecto, solo hace falta que el perfil apunte a donde sea que se instale.
 */
public final class InstallerUi {

    private final Config config;

    private JFrame frame;
    private JRadioButton profileOption;
    private JRadioButton folderOption;
    private JTextField profileNameField;
    private JLabel folderLabel;
    private JLabel statusLabel;
    private JProgressBar progress;
    private JButton installButton;
    private Path gameFolder;

    /** Todo lo que distingue a un modpack de otro. Nada de esto esta en el codigo. */
    public record Config(
            String packName,
            String brandName,
            String commandAlias,
            String packUrl,
            String fallbackPackUrl,
            String neoForgeVersion,
            String folderName,
            Path bootstrapJar) {}

    public InstallerUi(Config config) {
        this.config = config;
        this.gameFolder = Platform.minecraftFolder().resolve(config.folderName());
    }

    public void show() {
        frame = new JFrame(Messages.get("window.title", config.packName()));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        content.add(title(config.packName()));
        content.add(Box.createVerticalStrut(4));
        content.add(plain(Messages.get("intro")));
        content.add(Box.createVerticalStrut(14));

        boolean hasLauncher = Platform.hasOfficialLauncher();

        profileOption = new JRadioButton(Messages.get("option.profile"), hasLauncher);
        profileOption.setEnabled(hasLauncher);
        profileOption.addActionListener(event -> refreshEnabled());
        content.add(profileOption);
        content.add(hint(Messages.get(hasLauncher ? "option.profile.hint" : "option.profile.missing")));

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameRow.setBorder(BorderFactory.createEmptyBorder(0, 22, 6, 0));
        nameRow.add(plain(Messages.get("field.profileName") + "  "));
        profileNameField = new JTextField(config.packName(), 18);
        nameRow.add(profileNameField);
        content.add(nameRow);

        folderOption = new JRadioButton(Messages.get("option.folder"), !hasLauncher);
        folderOption.addActionListener(event -> refreshEnabled());
        content.add(folderOption);
        content.add(hint(Messages.get("option.folder.hint")));

        ButtonGroup group = new ButtonGroup();
        group.add(profileOption);
        group.add(folderOption);

        JPanel folderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        folderRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        folderRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JButton browse = new JButton(Messages.get("button.changeFolder"));
        browse.addActionListener(event -> chooseFolder());
        folderRow.add(browse);
        folderRow.add(Box.createHorizontalStrut(8));
        folderLabel = hint(gameFolder.toString());
        folderLabel.setBorder(BorderFactory.createEmptyBorder());
        folderRow.add(folderLabel);
        content.add(folderRow);

        content.add(Box.createVerticalStrut(12));
        content.add(memoryPanel());
        content.add(Box.createVerticalStrut(12));

        progress = new JProgressBar();
        progress.setVisible(false);
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(progress);

        statusLabel = plain(" ");
        content.add(statusLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        installButton = new JButton(Messages.get("button.install"));
        installButton.addActionListener(event -> install());
        buttons.add(installButton);
        content.add(Box.createVerticalStrut(10));
        content.add(buttons);

        refreshEnabled();

        frame.setContentPane(content);
        frame.pack();
        frame.setMinimumSize(new Dimension(560, frame.getHeight()));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void refreshEnabled() {
        profileNameField.setEnabled(profileOption.isSelected());
    }

    /** Deja a la vista cuanta memoria se asigna, y avisa si la computadora esta justa. */
    private JPanel memoryPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        int totalGb = Platform.totalMemoryGb();
        int heapGb = Platform.recommendedHeapGb();
        panel.add(plain(Messages.get(
                "memory.detected",
                totalGb > 0 ? totalGb + " GB" : Messages.get("memory.unknown"),
                heapGb)));

        if (Platform.isMemoryTight()) {
            JLabel warning = plain(Messages.get("memory.tight"));
            warning.setForeground(new Color(0xB8, 0x6A, 0x00));
            panel.add(warning);
        }
        return panel;
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(Messages.get("chooser.title"));
        chooser.setSelectedFile(gameFolder.toFile());
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            gameFolder = chooser.getSelectedFile().toPath();
            folderLabel.setText(gameFolder.toString());
            frame.pack();
        }
    }

    private void install() {
        boolean withProfile = profileOption.isSelected();
        Path minecraftFolder = Platform.minecraftFolder();

        if (withProfile) {
            if (!LauncherProfiles.exists(minecraftFolder)) {
                error(Messages.get("error.noLauncher"));
                return;
            }
            if (profileNameField.getText().isBlank()) {
                error(Messages.get("error.noName"));
                return;
            }
            if (LauncherProfiles.looksLikeLauncherRunning()) {
                int answer = JOptionPane.showConfirmDialog(
                        frame,
                        Messages.get("warn.launcherOpen"),
                        config.packName(),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) {
                    return;
                }
            }
        }

        String profileName = profileNameField.getText().trim();
        Installer.Options options = new Installer.Options(
                withProfile ? Installer.Destination.DEDICATED_PROFILE : Installer.Destination.CUSTOM_FOLDER,
                minecraftFolder,
                gameFolder,
                config.packUrl(),
                config.fallbackPackUrl(),
                config.neoForgeVersion(),
                profileName,
                profileKey(profileName),
                config.brandName(),
                config.commandAlias(),
                config.bootstrapJar());

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
                    finished(withProfile, profileName);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    statusLabel.setText(Messages.get("status.failed"));
                    error(Messages.get("error.failed", String.valueOf(cause.getMessage())));
                }
            }
        }.execute();
    }

    /** Clave interna del perfil, estable para que reinstalar no duplique entradas. */
    private String profileKey(String profileName) {
        String slug = profileName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return "packwarden-" + (slug.isBlank() ? config.folderName() : slug);
    }

    private void finished(boolean withProfile, String profileName) {
        String message = withProfile
                ? Messages.get("done.profile", profileName)
                : Messages.get("done.folder", gameFolder.toString());

        JOptionPane.showMessageDialog(frame, message, config.packName(), JOptionPane.INFORMATION_MESSAGE);

        // Se cierra del todo: el instalador se corre una sola vez y no tiene nada
        // mas que ofrecer despues de terminar.
        frame.dispose();
        System.exit(0);
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(frame, message, config.packName(), JOptionPane.ERROR_MESSAGE);
    }

    private static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() + 5f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel plain(String text) {
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
    public static void launch(Config config) {
        if (!Files.isRegularFile(config.bootstrapJar())) {
            JOptionPane.showMessageDialog(
                    null,
                    Messages.get("error.noBootstrap", config.bootstrapJar().toString()),
                    config.packName(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> new InstallerUi(config).show());
    }

    static Path resolve(String value) {
        return Paths.get(value);
    }
}
