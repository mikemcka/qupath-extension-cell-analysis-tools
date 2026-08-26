package qupath.ext.qpcat.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.qpcat.preferences.QpcatPreferences;
import qupath.ext.qpcat.service.ApposeClusteringService;

import java.io.File;
import java.nio.file.Path;

/**
 * Dialog for downloading and setting up the Python clustering environment.
 */
public class SetupEnvironmentDialog {

    private static final Logger logger = LoggerFactory.getLogger(SetupEnvironmentDialog.class);

    private final Stage owner;
    private final Runnable onComplete;
    private Stage dialog;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Button setupButton;
    private Button closeButton;
    private TextField dirField;
    private Button browseButton;

    public SetupEnvironmentDialog(Stage owner, Runnable onComplete) {
        this.owner = owner;
        this.onComplete = onComplete;
    }

    public void show() {
        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.NONE);
        dialog.setTitle("QPCAT - Environment Setup");

        statusLabel = new Label("Click 'Setup' to download and configure the Python environment.\n"
                + "This requires an internet connection and approximately 1.5-2.5 GB of disk space.\n"
                + "No GPU is required -- all clustering runs on CPU.");
        statusLabel.setWrapText(true);

        // Install-location chooser. The environment is created in a
        // "qupath-qpcat" subfolder of this base directory. HPC / managed
        // desktops often give the home directory a quota too small for the
        // ~2.5 GB environment, so users can redirect it to a scratch volume.
        Label dirLabel = new Label("Install location:");
        dirField = new TextField(effectiveBaseDir());
        dirField.setPrefWidth(320);
        dirField.setTooltip(Tooltips.of(
                "Base directory for the Python environment (created in a\n"
                + "'qupath-qpcat' subfolder). Leave as the default under your home\n"
                + "directory, or point it at a larger disk (e.g. scratch) if your\n"
                + "home has a small quota -- the environment needs ~2.5 GB."));
        HBox.setHgrow(dirField, Priority.ALWAYS);
        browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> chooseDirectory());
        HBox dirBox = new HBox(8, dirLabel, dirField, browseButton);
        dirBox.setAlignment(Pos.CENTER_LEFT);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(450);

        setupButton = new Button("Setup Environment");
        setupButton.setOnAction(e -> startSetup());
        setupButton.setTooltip(Tooltips.of(
                "Download and install the Python environment with\n"
                + "scikit-learn, scanpy, UMAP, and other dependencies.\n"
                + "Requires internet (~1.5-2.5 GB download)."));

        closeButton = new Button("Close");
        closeButton.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(10, setupButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox root = new VBox(15,
                QpcatDocLinks.linkBar("1-setting-up-the-environment"),
                statusLabel, dirBox, progressBar, buttonBox);
        root.setPadding(new Insets(20));
        root.setPrefWidth(500);

        dialog.setScene(new Scene(root));
        dialog.setResizable(false);
        dialog.show();
    }

    /**
     * The base directory currently in effect: the user preference if set,
     * otherwise Appose's default location under the home directory. Shown as the
     * prefilled value so the user always sees where the environment will land.
     */
    private static String effectiveBaseDir() {
        String pref = QpcatPreferences.getEnvBaseDir();
        if (pref != null && !pref.isBlank()) {
            return pref.strip();
        }
        return Path.of(System.getProperty("user.home"), ".local", "share", "appose").toString();
    }

    private void chooseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose environment install location");
        String current = dirField.getText();
        if (current != null && !current.isBlank()) {
            File dir = new File(current.strip());
            // Walk up to the first existing ancestor so the chooser opens somewhere valid.
            while (dir != null && !dir.isDirectory()) {
                dir = dir.getParentFile();
            }
            if (dir != null) {
                chooser.setInitialDirectory(dir);
            }
        }
        File chosen = chooser.showDialog(dialog);
        if (chosen != null) {
            dirField.setText(chosen.getAbsolutePath());
        }
    }

    private void startSetup() {
        // Persist the chosen install location before building so getEnvironmentPath()
        // and the Appose builder agree on where the environment goes. An empty
        // field, or the default location, is stored as "" to mean "use default".
        String chosen = dirField.getText() == null ? "" : dirField.getText().strip();
        String defaultBase = Path.of(System.getProperty("user.home"),
                ".local", "share", "appose").toString();
        QpcatPreferences.setEnvBaseDir(chosen.isBlank() || chosen.equals(defaultBase) ? "" : chosen);

        setupButton.setDisable(true);
        dirField.setDisable(true);
        browseButton.setDisable(true);
        progressBar.setProgress(-1);  // Indeterminate
        statusLabel.setText("Building Python environment...");

        Thread setupThread = new Thread(() -> {
            try {
                ApposeClusteringService.getInstance().initialize(
                        msg -> Platform.runLater(() -> statusLabel.setText(msg)));

                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    statusLabel.setText("Environment setup complete!");
                    setupButton.setDisable(true);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            } catch (Exception e) {
                logger.error("Environment setup failed", e);
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    statusLabel.setText("Setup failed: " + e.getMessage());
                    setupButton.setDisable(false);
                    dirField.setDisable(false);
                    browseButton.setDisable(false);
                });
            }
        }, "QPCAT-Setup");
        setupThread.setDaemon(true);
        setupThread.start();
    }
}
