package io.github.neo236.packwarden.companion;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
    private JComboBox<Language> languageBox;
    private Path gameFolder;
    private Language language = Language.fromSystem();

    /**
     * Idiomas ofrecidos.
     *
     * <p>Cada uno define dos cosas a la vez: en que idioma se ve el instalador y en
     * que idioma arranca el juego la primera vez. Son la misma eleccion para el
     * jugador, asi que preguntarla dos veces no tendria sentido.
     */
    enum Language {
        ES_ES("es", "es_es", "Español (España)"),
        ES_MX("es", "es_mx", "Español (Latinoamérica)"),
        ES_AR("es", "es_ar", "Español (Argentina)"),
        EN_US("en", "en_us", "English (US)"),
        PT_BR("pt", "pt_br", "Português (Brasil)");

        final String uiTag;
        final String minecraftCode;
        final String label;

        Language(String uiTag, String minecraftCode, String label) {
            this.uiTag = uiTag;
            this.minecraftCode = minecraftCode;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        /**
         * El que mejor encaja con el sistema.
         *
         * <p>Se cae a español de España, que es la variante mas neutra de las que
         * ofrece Minecraft y la que mas gente entiende.
         */
        static Language fromSystem() {
            String tag = Locale.getDefault().getLanguage();
            for (Language language : values()) {
                if (language.uiTag.equals(tag)) {
                    return language;
                }
            }
            return ES_ES;
        }
    }

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
        Messages.setLanguage(language.uiTag);
        frame = new JFrame(Messages.get("window.title", config.packName()));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        applyIcons(frame);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        content.add(title(config.packName()));
        content.add(Box.createVerticalStrut(4));
        content.add(plain(Messages.get("intro")));
        content.add(Box.createVerticalStrut(2));
        content.add(aviso(Messages.get("warn.closeLauncher")));
        content.add(Box.createVerticalStrut(10));
        content.add(languageRow());
        content.add(Box.createVerticalStrut(10));

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
        content.add(hint(Messages.get("option.folder.hint2")));

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

    /**
     * Selector de idioma.
     *
     * <p>Al cambiarlo se reconstruye la ventana entera. Es lo mas simple y ocurre
     * a lo sumo una vez: no vale la pena mantener referencias a cada etiqueta solo
     * para poder reescribirlas.
     */
    private JPanel languageRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(plain(Messages.get("field.language") + "  "));

        languageBox = new JComboBox<>(Language.values());
        languageBox.setSelectedItem(language);
        languageBox.addActionListener(event -> {
            Language chosen = (Language) languageBox.getSelectedItem();
            if (chosen != null && chosen != language) {
                language = chosen;
                frame.dispose();
                show();
            }
        });
        row.add(languageBox);
        return row;
    }

    /**
     * Icono de la ventana y de la barra de tareas.
     *
     * <p>Se cargan varios tamaños y el sistema elige el que necesita en cada lugar.
     * Si no estan, la ventana usa el icono generico de Java: es feo, pero no vale
     * la pena que falte un archivo y no abra.
     */
    private static void applyIcons(JFrame frame) {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
            try (InputStream in = InstallerUi.class.getResourceAsStream("/packwarden/icon-" + size + ".png")) {
                if (in != null) {
                    icons.add(ImageIO.read(in));
                }
            } catch (Exception ignored) {
                // Un icono ilegible no puede impedir que se abra el instalador.
            }
        }
        if (!icons.isEmpty()) {
            frame.setIconImages(icons);
        }
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
                language.minecraftCode,
                config.bootstrapJar());

        installButton.setEnabled(false);
        languageBox.setEnabled(false);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        frame.pack();

        SwingWorker<Void, String> trabajo = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                Installer.run(options, new Installer.Progress() {
                    @Override
                    public void step(String message) {
                        publish(message);
                    }

                    @Override
                    public void progress(int hechos, int total) {
                        // La barra pasa a mostrar cuantos archivos van: medio giga de
                        // descarga con una barra indeterminada parece un cuelgue.
                        setProgress(total > 0 ? Math.min(100, hechos * 100 / total) : 0);
                        publish(Messages.get("step.downloadingCount", hechos, total));
                    }
                });
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
        };

        // La barra pasa de indeterminada a real en cuanto llega el primer avance.
        trabajo.addPropertyChangeListener(evento -> {
            if ("progress".equals(evento.getPropertyName())) {
                progress.setIndeterminate(false);
                progress.setValue((Integer) evento.getNewValue());
            }
        });
        trabajo.execute();
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

    /** Aviso destacado: lo unico que el jugador tiene que hacer antes de empezar. */
    private static JLabel aviso(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setForeground(new Color(0xB8, 0x6A, 0x00));
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
