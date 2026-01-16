package com.natixis.etrading.gui.swing;

import com.natixis.etrading.gui.swing.controller.MainController;
import com.natixis.etrading.gui.swing.model.CsvRepository;
import com.natixis.etrading.gui.swing.model.UpdateEngine;
import com.natixis.etrading.gui.swing.view.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * Main Swing Application entry point.
 * 
 * This class bootstraps the application:
 * - Sets up a JIDE-compatible look and feel
 * - Creates the main window
 */
public class App {
    
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    
    public static void main(String[] args) {
        logger.info("Starting CSV Table Monitor (Swing)...");
        
        // Set up Metal LAF to avoid JIDE's Windows LAF dependency on non-Windows runtimes.
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            logger.warn("Failed to set cross-platform look and feel, using system default", e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                logger.error("Failed to set system look and feel", ex);
            }
        }
        
        // Create and show main window on EDT
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            MainController controller = new MainController(
                    mainFrame,
                    new CsvRepository(),
                    new UpdateEngine()
            );
            mainFrame.setController(controller);
            controller.onViewReady();
            mainFrame.setVisible(true);
            logger.info("Application started successfully");
        });
    }
}
