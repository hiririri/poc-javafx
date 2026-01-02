package com.csvmonitor.view;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main JavaFX Application entry point.
 * 
 * This class bootstraps the application:
 * - Creates the view programmatically via MainController
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
        
        // Create controller and build view programmatically
        controller = new MainController();
        Parent root = controller.createView();
        
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
    }
    
    @Override
    public void stop() {
        logger.info("Application stopped");
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
