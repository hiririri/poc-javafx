package com.natixis.etrading.gui.view;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class MainController extends Application implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final String APP_TITLE = "CSV Table Monitor";
    private static final int DEFAULT_WIDTH = 1200;
    private static final int DEFAULT_HEIGHT = 700;

    private List<TableController> controllers;
    private int tableCounter = 1;
    private Stage primaryStage;

    // FXML injected fields
    @FXML
    private MenuBar menuBar;
    @FXML
    private Menu fileMenu;
    @FXML
    private Menu optionsMenu;
    @FXML
    private Menu helpMenu;
    @FXML
    private MenuItem newTableItem;
    @FXML
    private MenuItem exitItem;
    @FXML
    private MenuItem preferencesItem;
    @FXML
    private MenuItem aboutItem;
    @FXML
    private DetachableTabPane tabPane;
    @FXML
    private SplitPane mainSplitPane;

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting {} application...", APP_TITLE);

        this.primaryStage = primaryStage;

        try {
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml"));
            VBox mainLayout = loader.load();

            // Create scene and apply CSS
            Scene scene = new Scene(mainLayout, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            try {
                scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
            } catch (Exception e) {
                logger.warn("Could not load styles.css: {}", e.getMessage());
            }

            // Configure stage
            primaryStage.setTitle(APP_TITLE);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(800);

            primaryStage.show();
            logger.info("Application started successfully");

        } catch (IOException e) {
            logger.error("Failed to load FXML", e);
            throw new RuntimeException("Failed to load FXML", e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing MainFrame...");

        // Initialize controllers list
        controllers = new ArrayList<>();

        // Configure DetachableTabPane
        tabPane.setOnClosedPassSibling((sibling) -> {
            // Update reference and refresh layout when tabs are merged
            tabPane = sibling;
            Platform.runLater(this::refreshSplitPaneLayout);
        });
        tabPane.setStageOwnerFactory((stage) -> primaryStage);

        // Set initial divider position
        mainSplitPane.setDividerPositions(1.0); // Start with tabPane taking full width

        // Create initial table view
        Platform.runLater(this::onNewTable);

        logger.info("MainFrame initialized");
    }

    // ==================== FXML Event Handlers ====================

    @FXML
    private void onNewTable() {
        logger.info("Creating new table view {}", tableCounter);

        try {
            TableController controller = new TableController();
            controllers.add(controller);

            // Create the table view
            StackPane tableContent = new StackPane();
            tableContent.getChildren().add(controller.createView());
            tableContent.setPadding(new Insets(5));

            // Create tab
            Tab tab = new Tab("Table " + tableCounter);
            tab.setContent(tableContent);
            tab.setClosable(true);

            // Handle tab close
            tab.setOnClosed(e -> {
                logger.info("Table tab closed");
                controller.shutdown();
                controllers.remove(controller);
            });

            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);

            tableCounter++;
            logger.info("New table view created successfully");
        } catch (Exception e) {
            logger.error("Failed to create new table view", e);
            showErrorDialog("Error", "Failed to create new table view: " + e.getMessage());
        }
    }

    @FXML
    private void onExit() {
        logger.info("Exit requested from menu");
        controllers.forEach(TableController::shutdown);
        System.exit(0);
    }

    @FXML
    private void onPreferences() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Preferences");
        alert.setHeaderText("Application Preferences");
        alert.setContentText("Preferences dialog not yet implemented.");
        alert.showAndWait();
    }

    @FXML
    private void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText(APP_TITLE);
        alert.setContentText("A JavaFX application for monitoring CSV table data.\n\nVersion: 1.0\nBuilt with JavaFX and TiwulFX.");
        alert.showAndWait();
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("An error occurred");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Refreshes the SplitPane layout after tab merge operations
     */
    private void refreshSplitPaneLayout() {
        if (mainSplitPane != null && tabPane != null) {
            logger.info("Refreshing SplitPane layout after tab merge");

            // Clear and re-add the tabPane to ensure clean layout
            mainSplitPane.getItems().clear();
            mainSplitPane.getItems().add(tabPane);
            mainSplitPane.setDividerPositions(1.0);

            // Force layout refresh
            mainSplitPane.autosize();
            mainSplitPane.requestLayout();

            // Also refresh the parent layout
            if (mainSplitPane.getParent() != null) {
                mainSplitPane.getParent().requestLayout();
            }

            logger.info("SplitPane layout refreshed successfully");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
