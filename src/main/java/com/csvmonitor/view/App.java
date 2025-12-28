package com.csvmonitor.view;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main JavaFX Application entry point.
 * 
 * This class bootstraps the application:
 * - Loads the FXML view
 * - Applies CSS styling
 * - Sets up shutdown hooks for cleanup
 */
public class App extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final String APP_TITLE = "CSV Table Monitor";
    private static final int DEFAULT_WIDTH = 1200;
    private static final int DEFAULT_HEIGHT = 700;
    
    private MainController controller;
    
    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting {} application...", APP_TITLE);
        
        try {
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/MainView.fxml"));
            Parent root = loader.load();
            controller = loader.getController();
            
            // Create scene and apply CSS
            Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
            
            // Configure stage
            primaryStage.setTitle(APP_TITLE);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(500);
            
            // Handle window close
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Application closing...");
                if (controller != null) {
                    controller.shutdown();
                }
            });
            
            primaryStage.show();
            logger.info("Application started successfully");
            
        } catch (IOException e) {
            logger.error("Failed to load FXML: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start application", e);
        }
    }
    
    @Override
    public void stop() {
        logger.info("Application stopped");
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}

