package com.csvmonitor.swing;

import com.csvmonitor.swing.view.MainFrame;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * Main Swing Application entry point.
 * 
 * This class bootstraps the application:
 * - Sets up FlatLaf look and feel for modern appearance
 * - Creates the main window
 */
public class App {
    
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    
    public static void main(String[] args) {
        logger.info("Starting CSV Table Monitor (Swing)...");
        
        // Set up FlatLaf look and feel
        try {
            FlatLightLaf.setup();
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", true);
            UIManager.put("Table.intercellSpacing", new java.awt.Dimension(1, 1));
        } catch (Exception e) {
            logger.warn("Failed to set FlatLaf look and feel, using system default", e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                logger.error("Failed to set system look and feel", ex);
            }
        }
        
        // Create and show main window on EDT
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
            logger.info("Application started successfully");
        });
    }
}

